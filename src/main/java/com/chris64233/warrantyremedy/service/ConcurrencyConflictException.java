package com.chris64233.warrantyremedy.service;

/** 并发竞争失败：替换品已被占用、申请已被并发批准等（HTTP 409）。 */
public class ConcurrencyConflictException extends BusinessRuleException {
    public ConcurrencyConflictException(String message) {
        super(message);
    }
}
