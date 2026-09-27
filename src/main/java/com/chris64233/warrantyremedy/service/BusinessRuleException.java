package com.chris64233.warrantyremedy.service;

/**
 * 业务规则冲突的基类（HTTP 409）。
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
