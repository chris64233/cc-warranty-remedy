package com.chris64233.warrantyremedy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chris64233.warrantyremedy.DatabaseCleaner;
import com.chris64233.warrantyremedy.FixedClockConfig;
import com.chris64233.warrantyremedy.TestFixtures;
import com.chris64233.warrantyremedy.api.Views.ClaimView;
import com.chris64233.warrantyremedy.api.Views.DispositionView;
import com.chris64233.warrantyremedy.api.Views.ProductView;
import com.chris64233.warrantyremedy.api.Views.WarrantyChainView;
import com.chris64233.warrantyremedy.service.dto.CorrectionCommand;
import com.chris64233.warrantyremedy.service.dto.SubmitClaimCommand;
import com.chris64233.warrantyremedy.service.dto.SubmitClaimCommand.EvidenceCommand;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 保修申请与三类处置的单线程业务规则集成测试。
 */
@SpringBootTest
@Import({FixedClockConfig.class, TestFixtures.class})
class ClaimWorkflowIntegrationTest {

    private static final LocalDate SALE_DATE = LocalDate.of(2025, 6, 1);

    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private ProductService productService;
    @Autowired
    private WarrantyClaimService claimService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        fixtures.registerProduct("SN-A", SALE_DATE, 12);
        fixtures.registerStock("SN-R1", 12);
        fixtures.registerStock("SN-R2", 12);
    }

    // ------------------------------------------------------------ 受理 / 资格

    @Test
    void submittingClaimFreezesEligibilityAndStoresEvidence() {
        Long id = fixtures.submitClaim("EXT-1", "SN-A", "屏幕不亮");

        ClaimView view = claimService.getClaimDetail(id);
        assertThat(view.status()).isEqualTo("SUBMITTED");
        assertThat(view.eligibility().eligible()).isTrue();
        assertThat(view.eligibility().evaluatedOn()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(view.evidences()).hasSize(1);
        assertThat(view.evidences().get(0).type()).isEqualTo("PURCHASE_PROOF");
    }

    @Test
    void claimWithoutPurchaseProofIsRejected() {
        List<EvidenceCommand> onlyPhoto =
                List.of(new EvidenceCommand("FAULT_PHOTO", "photo.jpg", null));
        assertThatThrownBy(() -> fixtures.submitClaim("EXT-X", "SN-A", "花屏", onlyPhoto))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("购买凭证");
    }

    @Test
    void claimWithEmptyEvidenceIsRejected() {
        assertThatThrownBy(() ->
                claimService.submit(new SubmitClaimCommand("EXT-X", "SN-A", "花屏", List.of())))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void duplicateExternalRefIsIdempotentAndReturnsSameClaim() {
        Long first = fixtures.submitClaim("DUP-1", "SN-A", "故障甲");
        ClaimView second = claimService.submit(
                new SubmitClaimCommand("DUP-1", "SN-A", "完全不同的故障内容也忽略",
                        TestFixtures.PROOF));
        assertThat(second.id()).isEqualTo(first);
        // 原申请内容不可被重复提交改变
        assertThat(second.symptom()).isEqualTo("故障甲");
    }

    @Test
    void sameProductSameSymptomCannotHaveTwoActiveClaims() {
        fixtures.submitClaim("ACT-1", "SN-A", "同一故障");
        assertThatThrownBy(() -> fixtures.submitClaim("ACT-2", "SN-A", "  同一故障 "))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("活动申请");
    }

    @Test
    void sameProductDifferentSymptomIsAllowed() {
        fixtures.submitClaim("DIFF-1", "SN-A", "故障一");
        Long second = fixtures.submitClaim("DIFF-2", "SN-A", "故障二");
        assertThat(second).isNotNull();
    }

    @Test
    void expiredProductCannotSubmitClaim() {
        fixtures.registerProduct("SN-OLD", LocalDate.of(2024, 1, 1), 12);
        assertThatThrownBy(() -> fixtures.submitClaim("EXP-1", "SN-OLD", "开不了机"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("WARRANTY_EXPIRED");
    }

    @Test
    void unknownSerialIsRejectedOnSubmit() {
        assertThatThrownBy(() -> fixtures.submitClaim("NO-1", "SN-404", "故障"))
                .isInstanceOf(ValidationException.class);
    }

    // ------------------------------------------------------------ 维修

    @Test
    void repairLifecycleKeepsProductAndWarrantyIntact() {
        Long id = fixtures.submitClaim("RP-1", "SN-A", "电池鼓包");
        fixtures.approveRepair(id);

        ClaimView approved = claimService.getClaimDetail(id);
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.disposition().type()).isEqualTo("REPAIR");
        assertThat(approved.disposition().status()).isEqualTo("APPROVED");

        DispositionView executed = claimService.execute(id);
        assertThat(executed.status()).isEqualTo("EXECUTED");

        ProductView product = productService.getBySerial("SN-A");
        assertThat(product.status()).isEqualTo("ACTIVE");
        assertThat(product.saleDate()).isEqualTo(SALE_DATE);
        assertThat(product.warrantyEnd()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void cannotApproveTwice() {
        Long id = fixtures.submitClaim("RP-2", "SN-A", "按键失灵");
        fixtures.approveRepair(id);
        assertThatThrownBy(() -> fixtures.approveRepair(id))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能批准");
    }

    @Test
    void executeWithoutApprovalIsRejected() {
        Long id = fixtures.submitClaim("RP-3", "SN-A", "无声音");
        assertThatThrownBy(() -> claimService.execute(id))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ------------------------------------------------------------ 换货

    @Test
    void replacementTransfersWarrantyToNewSerialAndBuildsChain() {
        Long id = fixtures.submitClaim("EX-1", "SN-A", "主板烧毁");
        fixtures.approveReplacement(id, "SN-R1");

        // 批准后、执行前：替换品被占用
        assertThat(productService.getBySerial("SN-R1").status()).isEqualTo("HELD");

        claimService.execute(id);

        ProductView oldProduct = productService.getBySerial("SN-A");
        ProductView newProduct = productService.getBySerial("SN-R1");
        assertThat(oldProduct.status()).isEqualTo("REPLACED");
        assertThat(oldProduct.replacedBySerial()).isEqualTo("SN-R1");
        assertThat(newProduct.status()).isEqualTo("ACTIVE");
        assertThat(newProduct.replacedFromSerial()).isEqualTo("SN-A");
        // 保修关系继承：销售日期与保修期限不变
        assertThat(newProduct.saleDate()).isEqualTo(SALE_DATE);
        assertThat(newProduct.warrantyEnd()).isEqualTo(LocalDate.of(2026, 6, 1));

        // 原序列号保修资格终止（同故障在新产品上可重新申请）
        assertThatThrownBy(() -> fixtures.submitClaim("EX-1B", "SN-A", "主板烧毁"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("PRODUCT_REPLACED");

        WarrantyChainView chain = productService.warrantyChain("SN-R1");
        assertThat(chain.chain()).extracting(ProductView::serialNumber)
                .containsExactly("SN-A", "SN-R1");
        assertThat(chain.currentSerial()).isEqualTo("SN-R1");
    }

    @Test
    void approvingReplacementForUnknownOrNonStockUnitFailsAndLeavesNothingHeld() {
        Long id = fixtures.submitClaim("EX-2", "SN-A", "故障");

        // 不存在的替换品
        assertThatThrownBy(() -> fixtures.approveReplacement(id, "SN-404"))
                .isInstanceOf(ResourceNotFoundException.class);

        // 替换品不能是故障机自身
        assertThatThrownBy(() -> fixtures.approveReplacement(id, "SN-A"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("自身");

        // 失败后没有任何处置产生，申请仍可批准
        assertThat(claimService.getClaimDetail(id).status()).isEqualTo("SUBMITTED");
        assertThat(productService.listStockUnits()).extracting(ProductView::serialNumber)
                .containsExactlyInAnyOrder("SN-R1", "SN-R2");
    }

    @Test
    void cancellationBeforeExecutionReleasesHeldReplacement() {
        Long id = fixtures.submitClaim("EX-3", "SN-A", "故障");
        fixtures.approveReplacement(id, "SN-R1");
        assertThat(productService.getBySerial("SN-R1").status()).isEqualTo("HELD");

        claimService.cancel(id, "客户取消");

        assertThat(productService.getBySerial("SN-R1").status()).isEqualTo("IN_STOCK");
        ClaimView view = claimService.getClaimDetail(id);
        assertThat(view.status()).isEqualTo("CANCELLED");
        assertThat(view.cancelReason()).isEqualTo("客户取消");
        assertThat(view.disposition().status()).isEqualTo("CANCELLED");

        // 释放的替换品可被另一笔换货申请重新占用
        Long otherId = fixtures.submitClaim("EX-3B", "SN-A", "另一故障");
        fixtures.approveReplacement(otherId, "SN-R1");
        assertThat(productService.getBySerial("SN-R1").status()).isEqualTo("HELD");
    }

    @Test
    void cancelledClaimAllowsSameFaultClaimAgain() {
        Long id = fixtures.submitClaim("EX-4", "SN-A", "可复现故障");
        fixtures.approveRepair(id);
        claimService.cancel(id, null);
        // 终态释放活动去重键
        Long reopened = fixtures.submitClaim("EX-5", "SN-A", "可复现故障");
        assertThat(reopened).isNotNull();
    }

    @Test
    void executedDispositionCannotBeCancelled() {
        Long id = fixtures.submitClaim("EX-6", "SN-A", "故障");
        fixtures.approveReplacement(id, "SN-R1");
        claimService.execute(id);
        assertThatThrownBy(() -> claimService.cancel(id, "想撤销"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("不能撤销");
        // 换货已生效，不回退
        assertThat(productService.getBySerial("SN-A").status()).isEqualTo("REPLACED");
    }

    // ------------------------------------------------------------ 退款

    @Test
    void refundTerminatesWarrantyEligibility() {
        Long id = fixtures.submitClaim("RF-1", "SN-A", "无法修复");
        fixtures.approveRefund(id, 100_00L);
        claimService.execute(id);

        assertThat(productService.getBySerial("SN-A").status()).isEqualTo("REFUNDED");
        assertThat(claimService.getClaimDetail(id).disposition().refundAmountCents()).isEqualTo(100_00L);

        assertThatThrownBy(() -> fixtures.submitClaim("RF-1B", "SN-A", "无法修复"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("WARRANTY_TERMINATED");
    }

    @Test
    void negativeRefundAmountIsRejected() {
        Long id = fixtures.submitClaim("RF-2", "SN-A", "故障");
        assertThatThrownBy(() -> fixtures.approveRefund(id, -1L))
                .isInstanceOf(ValidationException.class);
    }

    // ------------------------------------------------------------ 纠正 / 查询 / 不可变

    @Test
    void executedDispositionOnlyAcceptsAppendedCorrections() {
        Long id = fixtures.submitClaim("CO-1", "SN-A", "故障");
        fixtures.approveRepair(id);
        claimService.execute(id);

        // 未执行状态不能追加纠正（此处置已执行，故测另一笔未执行的）
        Long pendingId = fixtures.submitClaim("CO-2", "SN-A", "另一个故障");
        fixtures.approveRepair(pendingId);
        assertThatThrownBy(() -> claimService.addCorrection(pendingId,
                new CorrectionCommand("REWORK", "想返工", null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("已执行");

        claimService.addCorrection(id, new CorrectionCommand("REWORK", "维修返工一次", null));
        claimService.addCorrection(id, new CorrectionCommand("ADDITIONAL_REFUND", "补偿运费", 2500L));

        ClaimView view = claimService.getClaimDetail(id);
        assertThat(view.disposition().corrections()).hasSize(2);
        assertThat(view.disposition().corrections().get(0).type()).isEqualTo("REWORK");
        assertThat(view.disposition().corrections().get(1).amountCents()).isEqualTo(2500L);
    }

    @Test
    void originalClaimAndDecisionRemainImmutableAcrossLifecycle() {
        Long id = fixtures.submitClaim("IM-1", "SN-A", "原始故障描述");
        ClaimView submitted = claimService.getClaimDetail(id);
        fixtures.approveRepair(id);
        claimService.execute(id);
        claimService.addCorrection(id, new CorrectionCommand("OTHER", "补充说明", null));

        ClaimView finalView = claimService.getClaimDetail(id);
        assertThat(finalView.symptom()).isEqualTo("原始故障描述");
        assertThat(finalView.externalRef()).isEqualTo(submitted.externalRef());
        assertThat(finalView.eligibility().evaluatedOn()).isEqualTo(submitted.eligibility().evaluatedOn());
        assertThat(finalView.eligibility().eligible()).isTrue();
        assertThat(finalView.submittedAt()).isEqualTo(submitted.submittedAt());
    }

    @Test
    void dispositionHistoryQueryCoversOriginalAndReplacementSerials() {
        Long first = fixtures.submitClaim("HQ-1", "SN-A", "第一次故障");
        fixtures.approveReplacement(first, "SN-R1");
        claimService.execute(first);

        // 新产品（承接保修）发生第二笔换货
        fixtures.registerStock("SN-R3", 12);
        Long second = fixtures.submitClaim("HQ-2", "SN-R1", "第二次故障");
        fixtures.approveReplacement(second, "SN-R3");
        claimService.execute(second);

        List<DispositionView> historyOnOld = claimService.getDispositions("SN-A");
        List<DispositionView> historyOnMid = claimService.getDispositions("SN-R1");

        assertThat(historyOnOld).hasSize(1);
        assertThat(historyOnOld.get(0).replacementSerial()).isEqualTo("SN-R1");
        // SN-R1 既是第一笔的替换品、又是第二笔的申请产品，两条记录都应查到
        assertThat(historyOnMid).hasSize(2);
    }
}
