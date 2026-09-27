package com.chris64233.warrantyremedy.service.dto;

/**
 * 批准处置的输入。
 *
 * @param type              REPAIR / REPLACEMENT / REFUND
 * @param replacementSerial REPLACEMENT 必填：要原子占用的替换品序列号
 * @param refundAmountCents REFUND 可选登记的退款金额（分）
 * @param note              决定理由
 */
public record ApproveDispositionCommand(
        String type,
        String replacementSerial,
        Long refundAmountCents,
        String note) {
}
