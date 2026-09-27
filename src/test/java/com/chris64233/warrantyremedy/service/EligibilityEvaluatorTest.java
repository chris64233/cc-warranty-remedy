package com.chris64233.warrantyremedy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chris64233.warrantyremedy.domain.EligibilityDecision;
import com.chris64233.warrantyremedy.domain.Product;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 保修资格判断单元测试：保修到期边界、换货后、退款后、库存品、未销售。
 */
class EligibilityEvaluatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
    private final EligibilityEvaluator evaluator =
            new EligibilityEvaluator(Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

    @Test
    void activeProductWithinWarrantyIsEligible() {
        Product p = Product.registered("SN-1", LocalDate.of(2025, 6, 1), 12, LocalDateTime.now());
        EligibilityDecision d = evaluator.evaluate(p);
        assertThat(d.isEligible()).isTrue();
        assertThat(d.getWarrantyEnd()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void warrantyEndDayIsInclusive() {
        // 销售日期 2025-01-15 + 12 个月 = 2026-01-15，恰好为基准日，仍合格
        Product p = Product.registered("SN-2", LocalDate.of(2025, 1, 15), 12, LocalDateTime.now());
        assertThat(evaluator.evaluate(p).isEligible()).isTrue();
    }

    @Test
    void dayAfterWarrantyEndIsIneligibleWithExpiredReason() {
        Product p = Product.registered("SN-3", LocalDate.of(2025, 1, 14), 12, LocalDateTime.now());
        EligibilityDecision d = evaluator.evaluate(p);
        assertThat(d.isEligible()).isFalse();
        assertThat(d.getReason()).isEqualTo("WARRANTY_EXPIRED");
    }

    @Test
    void replacedProductLosesEligibilityBecauseWarrantyMoved() {
        Product original = Product.registered("SN-OLD", LocalDate.of(2025, 6, 1), 12, LocalDateTime.now());
        Product replacement = Product.stockUnit("SN-NEW", 12, LocalDateTime.now());
        replacement.inheritWarrantyFrom(original);

        EligibilityDecision oldDecision = evaluator.evaluate(original);
        assertThat(oldDecision.isEligible()).isFalse();
        assertThat(oldDecision.getReason()).isEqualTo("PRODUCT_REPLACED");

        EligibilityDecision newDecision = evaluator.evaluate(replacement);
        assertThat(newDecision.isEligible()).isTrue();
        // 保修关系（销售日期）转移：新序列号沿用原销售日期
        assertThat(replacement.getSaleDate()).isEqualTo(LocalDate.of(2025, 6, 1));
    }

    @Test
    void refundedProductHasTerminatedWarranty() {
        Product p = Product.registered("SN-4", LocalDate.of(2025, 6, 1), 12, LocalDateTime.now());
        p.markRefunded();
        EligibilityDecision d = evaluator.evaluate(p);
        assertThat(d.isEligible()).isFalse();
        assertThat(d.getReason()).isEqualTo("WARRANTY_TERMINATED");
    }

    @Test
    void stockUnitHasNoWarrantyRelationship() {
        Product p = Product.stockUnit("SN-STOCK", 12, LocalDateTime.now());
        EligibilityDecision d = evaluator.evaluate(p);
        assertThat(d.isEligible()).isFalse();
        assertThat(d.getReason()).isEqualTo("NOT_SOLD");
        assertThat(d.getWarrantyEnd()).isNull();
    }
}
