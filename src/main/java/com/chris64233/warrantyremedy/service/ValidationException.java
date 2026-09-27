package com.chris64233.warrantyremedy.service;

/** 请求内容不满足前置条件，如缺少购买凭证、申请不符合保修资格（HTTP 422）。 */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
