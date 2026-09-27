package com.chris64233.warrantyremedy.domain;

/** 替换品库存状态。 */
public enum ReplacementUnitStatus {
    AVAILABLE,
    /** 已被某笔待执行的换货处置锁定。 */
    LOCKED,
    /** 已随换货执行出库，序列号转移给新产品实例。 */
    CONSUMED
}
