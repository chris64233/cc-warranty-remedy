package com.chris64233.warrantyremedy.domain;

/** 产品实例状态。 */
public enum ProductStatus {
    /** 正常在售后期内，可申请保修。 */
    ACTIVE,
    /** 已换货，保修资格转移至新序列号。 */
    REPLACED,
    /** 已退款，保修资格终止。 */
    REFUNDED
}
