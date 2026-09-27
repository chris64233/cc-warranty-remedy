package com.chris64233.warrantyremedy.service;

/** 资源不存在（HTTP 404）。 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
