package com.chris64233.warrantyremedy.service;

import com.chris64233.warrantyremedy.domain.EligibilityDecision;
import com.chris64233.warrantyremedy.domain.Product;
import com.chris64233.warrantyremedy.domain.ProductStatus;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 保修资格判断。规则：
 * <ul>
 *   <li>仅 {@link ProductStatus#ACTIVE} 且在保修到期日（含）之内的产品合格；</li>
 *   <li>REPLACED：保修关系已转移到后继序列号；REFUNDED：保修资格已终止；</li>
 *   <li>IN_STOCK/HELD：尚未销售，无保修关系；过期：超过保修到期日。</li>
 * </ul>
 * 判断结果（含基准日、到期日、原因）随申请固化，后续不随时间变化。
 */
@Component
public class EligibilityEvaluator {

    private final Clock clock;

    public EligibilityEvaluator(Clock clock) {
        this.clock = clock;
    }

    public EligibilityDecision evaluate(Product product) {
        return evaluate(product, LocalDate.now(clock));
    }

    EligibilityDecision evaluate(Product product, LocalDate today) {
        LocalDate end = product.warrantyEndDate();
        ProductStatus status = product.getStatus();

        if (status == ProductStatus.ACTIVE && end != null && !today.isAfter(end)) {
            return new EligibilityDecision(true, today, end, null,
                    "在保修期内（到期日 " + end + "）");
        }

        String reason;
        String note;
        switch (status) {
            case ACTIVE -> {
                reason = "WARRANTY_EXPIRED";
                note = "已超过保修到期日 " + end;
            }
            case REPLACED -> {
                reason = "PRODUCT_REPLACED";
                note = "产品已换货，保修关系已转移至后继序列号";
            }
            case REFUNDED -> {
                reason = "WARRANTY_TERMINATED";
                note = "产品已退款，保修资格已终止";
            }
            case IN_STOCK, HELD -> {
                reason = "NOT_SOLD";
                note = "产品尚未销售，不存在保修关系";
            }
            default -> {
                reason = "INELIGIBLE";
                note = "当前产品状态不符合保修条件：" + status;
            }
        }
        return new EligibilityDecision(false, today, end, reason, note);
    }
}
