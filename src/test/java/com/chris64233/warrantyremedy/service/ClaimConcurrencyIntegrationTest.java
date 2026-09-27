package com.chris64233.warrantyremedy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.warrantyremedy.DatabaseCleaner;
import com.chris64233.warrantyremedy.FixedClockConfig;
import com.chris64233.warrantyremedy.TestFixtures;
import com.chris64233.warrantyremedy.api.Views.ClaimView;
import com.chris64233.warrantyremedy.api.Views.ProductView;
import com.chris64233.warrantyremedy.domain.ProductStatus;
import com.chris64233.warrantyremedy.repo.ProductRepository;
import com.chris64233.warrantyremedy.service.dto.ApproveDispositionCommand;
import com.chris64233.warrantyremedy.service.dto.SubmitClaimCommand;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 并发规则测试：
 * <ul>
 *   <li>并发批准同一申请只有一笔成功；</li>
 *   <li>并发为多个申请占用同一替换品只有一笔成功，且不存在"被占用但未关联处置"的替换品；</li>
 *   <li>并发以相同外部申请号提交，所有调用得到同一笔申请（幂等）；</li>
 *   <li>并发以不同外部申请号提交同产品同故障，只有一笔活动申请成功；</li>
 *   <li>占用后事务内后续步骤失败必须整体回滚，替换品回到库存。</li>
 * </ul>
 */
@SpringBootTest
@Import({FixedClockConfig.class, TestFixtures.class})
class ClaimConcurrencyIntegrationTest {

    private static final LocalDate SALE_DATE = LocalDate.of(2025, 6, 1);
    private static final int THREADS = 8;

    @Autowired
    private TestFixtures fixtures;
    @Autowired
    private WarrantyClaimService claimService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DatabaseCleaner.clean(jdbcTemplate);
        fixtures.registerProduct("SN-P1", SALE_DATE, 12);
        fixtures.registerProduct("SN-P2", SALE_DATE, 12);
        fixtures.registerStock("SN-SOLE", 12);
        for (int i = 0; i < THREADS; i++) {
            fixtures.registerProduct("SN-OP-" + i, SALE_DATE, 12);
        }
    }

    private record Outcome(boolean success, String error) {
    }

    private List<Outcome> runConcurrently(RunnableThrowing task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                int idx = i;
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    try {
                        task.run(idx);
                        return new Outcome(true, null);
                    } catch (RuntimeException e) {
                        return new Outcome(false, e.getClass().getSimpleName() + ":" + e.getMessage());
                    }
                }));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface RunnableThrowing {
        void run(int idx);
    }

    @Test
    void concurrentApproveOfSameClaimOnlyOneSucceeds() throws Exception {
        Long claimId = fixtures.submitClaim("CC-SAME", "SN-P1", "故障");

        List<Outcome> outcomes = runConcurrently(idx ->
                claimService.approve(claimId, new ApproveDispositionCommand("REPAIR", null, null, "维修")));

        long successes = outcomes.stream().filter(Outcome::success).count();
        assertThat(successes).as("并发批准结果：%s", outcomes).isEqualTo(1);
        assertThat(claimService.getClaimDetail(claimId).status()).isEqualTo("APPROVED");
    }

    @Test
    void concurrentHoldOfSameReplacementOnlyOneSucceedsWithNoOrphan() throws Exception {
        // 8 笔不同产品、不同故障的申请，并发要求换货并指定同一个库存替换品
        List<Long> claimIds = IntStream.range(0, THREADS)
                .mapToObj(i -> fixtures.submitClaim("CC-REPL-" + i, "SN-OP-" + i, "故障-" + i))
                .toList();

        List<Outcome> outcomes = runConcurrently(idx -> claimService.approve(
                claimIds.get(idx),
                new ApproveDispositionCommand("REPLACEMENT", "SN-SOLE", null, "换货")));

        long successes = outcomes.stream().filter(Outcome::success).count();
        assertThat(successes).as("并发占用结果：%s", outcomes).isEqualTo(1);

        ProductView sole = productService.getBySerial("SN-SOLE");
        assertThat(sole.status()).isEqualTo("HELD");
        // 库存中不再有该替换品
        assertThat(productService.listStockUnits()).extracting(ProductView::serialNumber)
                .doesNotContain("SN-SOLE");

        // 恰好一笔处置关联到该替换品；失败的申请仍停留在 SUBMITTED、无处置
        long approvedWithSole = claimIds.stream()
                .map(claimService::getClaimDetail)
                .peek(c -> {
                    if (!"APPROVED".equals(c.status())) {
                        assertThat(c.status()).isEqualTo("SUBMITTED");
                        assertThat(c.disposition()).isNull();
                    }
                })
                .filter(c -> c.status().equals("APPROVED")
                        && "SN-SOLE".equals(c.disposition().replacementSerial()))
                .count();
        assertThat(approvedWithSole).isEqualTo(1);

        // 撤销胜者后替换品释放，可立即被原本失败的申请占用
        Long winnerId = claimIds.stream()
                .filter(id -> "APPROVED".equals(claimService.getClaimDetail(id).status()))
                .findFirst().orElseThrow();
        claimService.cancel(winnerId, "测试释放");
        assertThat(productService.getBySerial("SN-SOLE").status()).isEqualTo("IN_STOCK");
        Long loserId = claimIds.stream().filter(id -> !id.equals(winnerId)).findFirst().orElseThrow();
        claimService.approve(loserId,
                new ApproveDispositionCommand("REPLACEMENT", "SN-SOLE", null, "换货重试"));
        assertThat(productService.getBySerial("SN-SOLE").status()).isEqualTo("HELD");
    }

    @Test
    void concurrentSubmitSameExternalRefAllReturnSameClaim() throws Exception {
        List<Outcome> outcomes = runConcurrently(idx -> claimService.submit(
                new SubmitClaimCommand("CC-DUP-REF", "SN-P1", "同一故障", TestFixtures.PROOF)));

        assertThat(outcomes).allMatch(Outcome::success, "并发同号提交都应幂等成功：%s".formatted(outcomes));
        ClaimView view = claimService.getClaimDetail("CC-DUP-REF");
        assertThat(view.externalRef()).isEqualTo("CC-DUP-REF");
        assertThat(view.status()).isEqualTo("SUBMITTED");
    }

    @Test
    void concurrentSubmitSameFaultDifferentRefsOnlyOneActive() throws Exception {
        List<Outcome> outcomes = runConcurrently(idx -> claimService.submit(
                new SubmitClaimCommand("CC-FAULT-" + idx, "SN-P2", "并发同一故障", TestFixtures.PROOF)));

        long successes = outcomes.stream().filter(Outcome::success).count();
        assertThat(successes).as("同产品同故障并发提交：%s", outcomes).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> !o.success()))
                .allSatisfy(o -> assertThat(o.error()).contains("BusinessRuleException"));
    }

    @Test
    void failureAfterHoldRollsBackAndLeavesReplacementInStock() {
        Long claimId = fixtures.submitClaim("CC-FAIL", "SN-P1", "故障");
        String overlongNote = "X".repeat(600);

        // 占用替换品成功后，处置写入因字段超长在提交时失败 -> 整个事务必须回滚
        try {
            claimService.approve(claimId,
                    new ApproveDispositionCommand("REPLACEMENT", "SN-SOLE", null, overlongNote));
            org.assertj.core.api.Assertions.fail("应当因字段超长提交失败");
        } catch (RuntimeException expected) {
            // 事务回滚
        }

        // 未留下"被占用但未关联处置"的替换品
        ProductView sole = productService.getBySerial("SN-SOLE");
        assertThat(sole.status()).isEqualTo(ProductStatus.IN_STOCK.name());
        // 申请回到无处置的 SUBMITTED，仍可正常批准
        ClaimView claim = claimService.getClaimDetail(claimId);
        assertThat(claim.status()).isEqualTo("SUBMITTED");
        assertThat(claim.disposition()).isNull();

        claimService.approve(claimId,
                new ApproveDispositionCommand("REPLACEMENT", "SN-SOLE", null, "重新换货"));
        assertThat(productService.getBySerial("SN-SOLE").status()).isEqualTo("HELD");
    }

    @Test
    void conditionalUpdateWithinRolledBackTransactionIsUndone() {
        Long stockId = productRepository.findBySerialNumber("SN-SOLE").orElseThrow().getId();
        // 直接验证：条件更新（IN_STOCK -> HELD）也参与事务回滚
        try {
            transactionTemplate.executeWithoutResult(status -> {
                int updated = productRepository.holdIfInStock(stockId);
                assertThat(updated).isEqualTo(1);
                throw new RuntimeException("模拟占用后步骤失败");
            });
        } catch (RuntimeException expected) {
            // 模拟失败，预期回滚
        }
        assertThat(productService.getBySerial("SN-SOLE").status()).isEqualTo("IN_STOCK");
    }
}
