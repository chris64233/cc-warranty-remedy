package com.chris64233.warrantyremedy.domain;

/**
 * 保修申请状态。
 *
 * <p>状态机：{@code SUBMITTED -> APPROVED -> EXECUTED}；{@code SUBMITTED/APPROVED} 可进入
 * {@link #CANCELLED}；{@link #EXECUTED} 为终态，只能追加纠正记录。
 */
public enum ClaimStatus {
    /** 已受理，尚未批准。 */
    SUBMITTED,
    /** 处置已批准（换货时替换品已占用），尚未实际执行。 */
    APPROVED,
    /** 处置已执行（维修完成/换货完成/退款完成），终态。 */
    EXECUTED,
    /** 未执行即撤销，资源已释放，终态。 */
    CANCELLED
}
