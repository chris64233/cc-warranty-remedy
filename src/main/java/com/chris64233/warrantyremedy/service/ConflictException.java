package com.chris64233.warrantyremedy.service;

/** 业务状态冲突（重复申请、非法状态流转等）。 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
