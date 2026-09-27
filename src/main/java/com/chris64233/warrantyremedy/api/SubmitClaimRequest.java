package com.chris64233.warrantyremedy.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitClaimRequest(
        @NotBlank String externalClaimNo,
        @NotNull Long productId,
        @NotBlank String faultCode,
        @NotBlank String faultDescription,
        @NotBlank String proofOfPurchase) {
}
