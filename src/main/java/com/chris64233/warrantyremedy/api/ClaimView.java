package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.domain.ClaimStatus;
import com.chris64233.warrantyremedy.domain.EligibilityDecision;
import com.chris64233.warrantyremedy.domain.WarrantyClaim;

import java.time.Instant;

/** 保修申请视图，包含申请证据（故障现象、购买凭证）与资格判断结果。 */
public record ClaimView(
        Long id,
        String externalClaimNo,
        Long productId,
        String faultCode,
        String faultDescription,
        String proofOfPurchase,
        ClaimStatus status,
        Boolean eligible,
        String eligibilityReason,
        Instant eligibilityDecidedAt,
        Instant createdAt) {

    public static ClaimView of(WarrantyClaim claim, EligibilityDecision decision) {
        return new ClaimView(
                claim.getId(),
                claim.getExternalClaimNo(),
                claim.getProduct().getId(),
                claim.getFaultCode(),
                claim.getFaultDescription(),
                claim.getProofOfPurchase(),
                claim.getStatus(),
                decision == null ? null : decision.isEligible(),
                decision == null ? null : decision.getReason(),
                decision == null ? null : decision.getDecidedAt(),
                claim.getCreatedAt());
    }
}
