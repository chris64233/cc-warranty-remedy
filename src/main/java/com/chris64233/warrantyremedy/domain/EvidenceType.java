package com.chris64233.warrantyremedy.domain;

/**
 * 申请证据类型。保修申请必须包含至少一份 {@link #PURCHASE_PROOF}（购买凭证）。
 */
public enum EvidenceType {
    PURCHASE_PROOF,
    FAULT_PHOTO,
    FAULT_VIDEO,
    INSPECTION_REPORT,
    OTHER
}
