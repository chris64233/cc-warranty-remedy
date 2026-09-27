package com.chris64233.warrantyremedy.service.dto;

import java.util.List;

/**
 * 提交保修申请的输入。证据列表至少包含一份 PURCHASE_PROOF。
 */
public record SubmitClaimCommand(
        String externalRef,
        String serialNumber,
        String symptom,
        List<EvidenceCommand> evidences) {

    public record EvidenceCommand(String type, String reference, String note) {
    }
}
