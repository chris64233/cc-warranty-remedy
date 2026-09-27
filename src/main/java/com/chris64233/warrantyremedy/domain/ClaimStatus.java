package com.chris64233.warrantyremedy.domain;

/** 保修申请状态。 */
public enum ClaimStatus {
    /** 已提交，等待批准（活动申请）。 */
    OPEN,
    /** 已批准并生成处置决定，处置未执行（活动申请）。 */
    APPROVED,
    /** 处置已执行或申请已关闭。 */
    CLOSED,
    /** 资格判断不通过，申请被拒绝。 */
    REJECTED
}
