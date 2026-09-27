package com.chris64233.warrantyremedy.domain;

/**
 * 处置状态。批准后为 {@link #APPROVED}（资源已预占但处置未生效），执行后进入 {@link #EXECUTED}；
 * 申请在执行前被撤销时处置一并 {@link #CANCELLED} 并释放其占用的资源。
 */
public enum DispositionStatus {
    APPROVED,
    EXECUTED,
    CANCELLED
}
