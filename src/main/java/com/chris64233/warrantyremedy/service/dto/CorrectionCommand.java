package com.chris64233.warrantyremedy.service.dto;

/**
 * 对已执行处置追加的纠正记录输入。
 */
public record CorrectionCommand(String type, String detail, Long amountCents) {
}
