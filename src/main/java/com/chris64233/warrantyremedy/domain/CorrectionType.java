package com.chris64233.warrantyremedy.domain;

/**
 * 纠正记录类型。
 */
public enum CorrectionType {
    /** 维修返工。 */
    REWORK,
    /** 追加退款 / 退款差额。 */
    ADDITIONAL_REFUND,
    /** 善意换货或换货补救。 */
    GOODWILL_REPLACEMENT,
    /** 其他说明性纠正。 */
    OTHER
}
