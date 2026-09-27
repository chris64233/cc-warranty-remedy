package com.chris64233.warrantyremedy.api;

import jakarta.validation.constraints.NotBlank;

public record CorrectionRequest(@NotBlank String note) {
}
