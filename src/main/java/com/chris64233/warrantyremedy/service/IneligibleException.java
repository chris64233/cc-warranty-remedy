package com.chris64233.warrantyremedy.service;

/** 产品不具备保修资格。 */
public class IneligibleException extends RuntimeException {
    public IneligibleException(String message) {
        super(message);
    }
}
