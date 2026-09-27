package com.chris64233.warrantyremedy.domain;

/** 处置执行状态。 */
public enum RemedyStatus {
    /** 已决定，尚未执行；可撤销并释放资源。 */
    PENDING,
    /** 已执行；不可撤销，只能追加纠正记录。 */
    EXECUTED,
    /** 已撤销，占用资源已释放。 */
    CANCELLED
}
