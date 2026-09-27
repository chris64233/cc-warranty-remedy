package com.chris64233.warrantyremedy;

import com.chris64233.warrantyremedy.api.ClaimView;
import com.chris64233.warrantyremedy.api.CorrectionView;
import com.chris64233.warrantyremedy.api.RegisterProductRequest;
import com.chris64233.warrantyremedy.api.RegisterReplacementUnitRequest;
import com.chris64233.warrantyremedy.api.RemedyView;
import com.chris64233.warrantyremedy.api.SubmitClaimRequest;
import com.chris64233.warrantyremedy.api.WarrantyChainView;
import com.chris64233.warrantyremedy.domain.ClaimStatus;
import com.chris64233.warrantyremedy.domain.ProductStatus;
import com.chris64233.warrantyremedy.domain.RemedyStatus;
import com.chris64233.warrantyremedy.domain.RemedyType;
import com.chris64233.warrantyremedy.domain.ReplacementUnitStatus;
import com.chris64233.warrantyremedy.service.ClaimService;
import com.chris64233.warrantyremedy.service.ConflictException;
import com.chris64233.warrantyremedy.service.NotFoundException;
import com.chris64233.warrantyremedy.service.ProductService;
import com.chris64233.warrantyremedy.service.RemedyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WarrantyRemedyFlowTest {

    @Autowired
    ProductService productService;
    @Autowired
    ClaimService claimService;
    @Autowired
    RemedyService remedyService;

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private long registerProduct(int warrantyMonths) {
        return productService.register(new RegisterProductRequest(
                unique("SN"), LocalDate.now().minusMonths(1), warrantyMonths)).id();
    }

    private ClaimView submitClaim(long productId, String faultCode, String externalNo) {
        return claimService.submit(new SubmitClaimRequest(
                externalNo, productId, faultCode, "无法开机", "发票 INV-001"));
    }

    /** 制造一笔“同故障已执行维修”的历史，返回产品 id。 */
    private long productWithExecutedRepair(String faultCode) {
        long productId = registerProduct(12);
        ClaimView first = submitClaim(productId, faultCode, unique("EXT"));
        remedyService.execute(remedyService.approve(first.id()).id());
        return productId;
    }

    @Test
    void submitIsIdempotentByExternalClaimNo() {
        long productId = registerProduct(12);
        String externalNo = unique("EXT");

        ClaimView first = submitClaim(productId, "F01", externalNo);
        ClaimView second = submitClaim(productId, "F01", externalNo);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo(ClaimStatus.OPEN);
        assertThat(first.eligible()).isTrue();
        assertThat(first.eligibilityReason()).isEqualTo("在保修期内");
        assertThat(first.proofOfPurchase()).isEqualTo("发票 INV-001");
    }

    @Test
    void onlyOneActiveClaimPerProductAndFault() {
        long productId = registerProduct(12);
        submitClaim(productId, "F01", unique("EXT"));

        // 同一产品同一故障：冲突
        assertThatThrownBy(() -> submitClaim(productId, "F01", unique("EXT")))
                .isInstanceOf(ConflictException.class);
        // 同一产品不同故障：允许
        ClaimView otherFault = submitClaim(productId, "F02", unique("EXT"));
        assertThat(otherFault.status()).isEqualTo(ClaimStatus.OPEN);
    }

    @Test
    void expiredWarrantyIsRejectedAndCannotBeApproved() {
        long productId = productService.register(new RegisterProductRequest(
                unique("SN"), LocalDate.now().minusYears(3), 12)).id();

        ClaimView claim = submitClaim(productId, "F01", unique("EXT"));
        assertThat(claim.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(claim.eligible()).isFalse();
        assertThat(claim.eligibilityReason()).isEqualTo("已过保修期限");

        assertThatThrownBy(() -> remedyService.approve(claim.id()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void firstFaultIsApprovedAsRepairAndExecuted() {
        long productId = registerProduct(12);
        ClaimView claim = submitClaim(productId, "F01", unique("EXT"));

        RemedyView remedy = remedyService.approve(claim.id());
        assertThat(remedy.type()).isEqualTo(RemedyType.REPAIR);
        assertThat(remedy.status()).isEqualTo(RemedyStatus.PENDING);

        RemedyView executed = remedyService.execute(remedy.id());
        assertThat(executed.status()).isEqualTo(RemedyStatus.EXECUTED);
        assertThat(executed.executedAt()).isNotNull();
        // 维修不改变产品状态
        assertThat(productService.get(productId).status()).isEqualTo(ProductStatus.ACTIVE);
        // 申请关闭，同故障可再次申请
        assertThat(claimService.get(claim.id()).status()).isEqualTo(ClaimStatus.CLOSED);
        // 处置历史可查
        assertThat(remedyService.remediesOfProduct(productId))
                .extracting(RemedyView::id)
                .containsExactly(remedy.id());
    }

    @Test
    void repeatedFaultIsApprovedAsReplacementAndTransfersWarranty() {
        String fault = "F09";
        long productId = productWithExecutedRepair(fault);
        var unit = productService.registerReplacementUnit(
                new RegisterReplacementUnitRequest(unique("RPL")));

        ClaimView second = submitClaim(productId, fault, unique("EXT"));
        RemedyView remedy = remedyService.approve(second.id());
        assertThat(remedy.type()).isEqualTo(RemedyType.REPLACEMENT);
        assertThat(remedy.replacementUnitId()).isEqualTo(unit.id());
        // 批准即锁定替换品
        assertThat(productService.listReplacementUnits())
                .filteredOn(u -> u.id().equals(unit.id()))
                .singleElement()
                .satisfies(u -> {
                    assertThat(u.status()).isEqualTo(ReplacementUnitStatus.LOCKED);
                    assertThat(u.lockedByRemedyId()).isEqualTo(remedy.id());
                });

        RemedyView executed = remedyService.execute(remedy.id());
        assertThat(executed.newProductUnitId()).isNotNull();
        assertThat(executed.newSerialNumber()).isEqualTo(unit.serialNumber());

        // 原产品已换货，保修资格转移到新序列号（沿用原销售日期与保修期限）
        var original = productService.get(productId);
        var newUnit = productService.get(executed.newProductUnitId());
        assertThat(original.status()).isEqualTo(ProductStatus.REPLACED);
        assertThat(original.replacedByUnitId()).isEqualTo(newUnit.id());
        assertThat(newUnit.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(newUnit.warrantyEndDate()).isEqualTo(original.warrantyEndDate());
        assertThat(newUnit.originUnitId()).isEqualTo(productId);
        // 替换品已出库
        assertThat(productService.listReplacementUnits())
                .filteredOn(u -> u.id().equals(unit.id()))
                .singleElement()
                .extracting(u -> u.status())
                .isEqualTo(ReplacementUnitStatus.CONSUMED);

        // 保修链：原实例 -> 新实例
        WarrantyChainView chain = productService.warrantyChain(newUnit.id());
        assertThat(chain.chain()).extracting(c -> c.id())
                .containsExactly(productId, newUnit.id());
    }

    @Test
    void repeatedFaultWithoutStockFallsBackToRefundAndTerminatesEligibility() {
        String fault = "F10";
        long productId = productWithExecutedRepair(fault);

        ClaimView second = submitClaim(productId, fault, unique("EXT"));
        RemedyView remedy = remedyService.approve(second.id());
        assertThat(remedy.type()).isEqualTo(RemedyType.REFUND);

        remedyService.execute(remedy.id());
        assertThat(productService.get(productId).status()).isEqualTo(ProductStatus.REFUNDED);

        // 退款后保修资格终止：新申请资格判断不通过
        ClaimView after = submitClaim(productId, "F99", unique("EXT"));
        assertThat(after.status()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(after.eligible()).isFalse();
        assertThat(after.eligibilityReason()).isEqualTo("产品已退款，保修资格已终止");
    }

    @Test
    void cancelPendingReplacementReleasesUnitAndReopensClaim() {
        String fault = "F11";
        long productId = productWithExecutedRepair(fault);
        var unit = productService.registerReplacementUnit(
                new RegisterReplacementUnitRequest(unique("RPL")));

        ClaimView claim = submitClaim(productId, fault, unique("EXT"));
        RemedyView remedy = remedyService.approve(claim.id());
        assertThat(remedy.type()).isEqualTo(RemedyType.REPLACEMENT);

        RemedyView cancelled = remedyService.cancel(remedy.id());
        assertThat(cancelled.status()).isEqualTo(RemedyStatus.CANCELLED);
        // 替换品释放
        assertThat(productService.listReplacementUnits())
                .filteredOn(u -> u.id().equals(unit.id()))
                .singleElement()
                .satisfies(u -> {
                    assertThat(u.status()).isEqualTo(ReplacementUnitStatus.AVAILABLE);
                    assertThat(u.lockedByRemedyId()).isNull();
                });
        // 申请回到 OPEN，可重新批准
        assertThat(claimService.get(claim.id()).status()).isEqualTo(ClaimStatus.OPEN);
        RemedyView reApproved = remedyService.approve(claim.id());
        assertThat(reApproved.id()).isNotEqualTo(remedy.id());
        assertThat(reApproved.status()).isEqualTo(RemedyStatus.PENDING);
    }

    @Test
    void executedRemedyCannotBeCancelledButAcceptsCorrections() {
        long productId = registerProduct(12);
        ClaimView claim = submitClaim(productId, "F01", unique("EXT"));
        RemedyView remedy = remedyService.approve(claim.id());

        // 未执行时不可追加纠正记录
        assertThatThrownBy(() -> remedyService.addCorrection(remedy.id(), "提前纠正"))
                .isInstanceOf(ConflictException.class);

        remedyService.execute(remedy.id());
        // 已执行不可撤销
        assertThatThrownBy(() -> remedyService.cancel(remedy.id()))
                .isInstanceOf(ConflictException.class);
        // 已执行可追加纠正记录
        CorrectionView correction = remedyService.addCorrection(remedy.id(), "维修更换了主板");
        assertThat(correction.note()).isEqualTo("维修更换了主板");
        assertThat(remedyService.get(remedy.id()).corrections())
                .extracting(CorrectionView::note)
                .containsExactly("维修更换了主板");
    }

    @Test
    void approveIsIdempotentForSameClaim() {
        long productId = registerProduct(12);
        ClaimView claim = submitClaim(productId, "F01", unique("EXT"));

        RemedyView first = remedyService.approve(claim.id());
        RemedyView second = remedyService.approve(claim.id());

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(remedyService.remediesOfProduct(productId)).hasSize(1);
    }

    @Test
    void executeIsIdempotent() {
        long productId = registerProduct(12);
        ClaimView claim = submitClaim(productId, "F01", unique("EXT"));
        RemedyView remedy = remedyService.approve(claim.id());

        RemedyView first = remedyService.execute(remedy.id());
        RemedyView second = remedyService.execute(remedy.id());

        assertThat(first.status()).isEqualTo(RemedyStatus.EXECUTED);
        assertThat(second.status()).isEqualTo(RemedyStatus.EXECUTED);
        assertThat(second.executedAt()).isNotNull();
        assertThat(remedyService.remediesOfProduct(productId)).hasSize(1);
    }

    @Test
    void missingResourcesRaiseNotFound() {
        assertThatThrownBy(() -> remedyService.approve(999999L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> claimService.submit(new SubmitClaimRequest(
                unique("EXT"), 999999L, "F01", "无法开机", "发票")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> remedyService.execute(999999L))
                .isInstanceOf(NotFoundException.class);
    }
}
