package com.chris64233.warrantyremedy.api;

import jakarta.validation.constraints.NotBlank;

public record RegisterReplacementUnitRequest(@NotBlank String serialNumber) {
}
