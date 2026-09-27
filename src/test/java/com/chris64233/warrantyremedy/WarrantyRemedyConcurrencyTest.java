package com.chris64233.warrantyremedy;

import com.chris64233.warrantyremedy.api.ClaimView;
import com.chris64233.warrantyremedy.api.RegisterProductRequest;
import com.chris64233.warrantyremedy.api.RegisterReplacementUnitRequest;
import com.chris64233.warrantyremedy.api.RemedyView;
import com.chris64233.warrantyremedy.api.SubmitClaimRequest;
import com.chris64233.warrantyremedy.domain.RemedyStatus;
import com.chris64233.warrantyremedy.domain.RemedyType;
import com.chris64233.warrantyremedy.domain.ReplacementUnitStatus;
import com.chris64233.warrantyremedy.service.ClaimService;
import com.chris64233.warrantyremedy.service.ProductService;
import com.chris64233.warrantyremedy.service.RemedyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发语义：同一申请并发批准只生成一笔处置；
 * 多笔申请并发竞争同一替换品时只有一笔能占用，
 * 且不会留下被占用但未关联任何处置的替换品。
 */
@SpringBootTest
class WarrantyRemedyConcurrencyTest {

    @Autowired
    ProductService productService;
    @Autowired
    ClaimService claimService;
    @Autowired
    RemedyService remedyService;

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private long registerProduct() {
        return productService.register(new RegisterProductRequest(
                unique("SN"), LocalDate.now().minusMonths(1), 12)).id();
    }

    private ClaimView submitClaim(long productId, String faultCode) {
        return claimService.submit(new SubmitClaimRequest(
                unique("EXT"), productId, faultCode, "无法开机", "发票 INV-001"));
    }

    private <T> List<T> runConcurrently(List<java.util.concurrent.Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch gate = new CountDownLatch(1);
        List<java.util.concurrent.Callable<T>> gated = tasks.stream()
                .<java.util.concurrent.Callable<T>>map(task -> () -> {
                    gate.await();
                    return task.call();
                })
                .toList();
        List<Future<T>> futures = gated.stream().map(pool::submit).toList();
        gate.countDown();
        try {
            List<T> results = new java.util.ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentApproveOfSameClaimCreatesSingleRemedy() throws Exception {
        long productId = registerProduct();
        ClaimView claim = submitClaim(productId, "F01");

        int threads = 8;
        List<RemedyView> results = runConcurrently(IntStream.range(0, threads)
                .mapToObj(i -> (java.util.concurrent.Callable<RemedyView>) () -> remedyService.approve(claim.id()))
                .toList());

        // 所有线程都成功，但只产生一笔处置
        assertThat(results).hasSize(threads);
        assertThat(results).extracting(RemedyView::id).containsOnly(results.getFirst().id());
        assertThat(remedyService.remediesOfProduct(productId))
                .filteredOn(r -> r.status() != RemedyStatus.CANCELLED)
                .hasSize(1);
    }

    @Test
    void concurrentClaimsCompetingForOneReplacementUnit() throws Exception {
        // 两个产品各自有一笔同故障已执行的维修 -> 再次故障都会升级为换货
        String fault = "F20";
        long productA = registerProduct();
        long productB = registerProduct();
        remedyService.execute(remedyService.approve(submitClaim(productA, fault).id()).id());
        remedyService.execute(remedyService.approve(submitClaim(productB, fault).id()).id());

        // 库存仅一件替换品
        var unit = productService.registerReplacementUnit(
                new RegisterReplacementUnitRequest(unique("RPL")));

        ClaimView claimA = submitClaim(productA, fault);
        ClaimView claimB = submitClaim(productB, fault);

        List<RemedyView> results = runConcurrently(List.of(
                () -> remedyService.approve(claimA.id()),
                () -> remedyService.approve(claimB.id())));

        // 只有一笔换货占用替换品，另一笔降级为退款
        List<RemedyView> replacements = results.stream()
                .filter(r -> r.type() == RemedyType.REPLACEMENT)
                .toList();
        List<RemedyView> refunds = results.stream()
                .filter(r -> r.type() == RemedyType.REFUND)
                .toList();
        assertThat(replacements).hasSize(1);
        assertThat(refunds).hasSize(1);
        assertThat(replacements.getFirst().replacementUnitId()).isEqualTo(unit.id());

        // 替换品被唯一关联到处置，不存在“被占用但未关联”的替换品
        var locked = productService.listReplacementUnits().stream()
                .filter(u -> u.status() == ReplacementUnitStatus.LOCKED)
                .toList();
        assertThat(locked).hasSize(1);
        assertThat(locked.getFirst().lockedByRemedyId()).isEqualTo(replacements.getFirst().id());
    }

    @Test
    void concurrentSubmitWithSameExternalClaimNoIsIdempotent() throws Exception {
        long productId = registerProduct();
        String externalNo = unique("EXT");

        int threads = 6;
        List<ClaimView> results = runConcurrently(IntStream.range(0, threads)
                .mapToObj(i -> (java.util.concurrent.Callable<ClaimView>) () -> claimService.submit(
                        new SubmitClaimRequest(externalNo, productId, "F01", "无法开机", "发票")))
                .toList());

        assertThat(results).extracting(ClaimView::id).containsOnly(results.getFirst().id());
    }
}
