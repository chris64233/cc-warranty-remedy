package com.chris64233.warrantyremedy.domain;

/**
 * 产品状态。
 *
 * <ul>
 *   <li>{@link #IN_STOCK}：换货库存中的替换品，可被原子占用；</li>
 *   <li>{@link #HELD}：已被一笔已批准换货原子占用、处置尚未执行或撤销，期间不可被其他申请抢占；</li>
 *   <li>{@link #ACTIVE}：正常享有保修资格的序列化产品；</li>
 *   <li>{@link #REPLACED}：已通过换货被替换，保修关系转移至后继序列号；</li>
 *   <li>{@link #REFUNDED}：已退款，后续保修资格终止。</li>
 * </ul>
 */
public enum ProductStatus {
    IN_STOCK,
    HELD,
    ACTIVE,
    REPLACED,
    REFUNDED
}
