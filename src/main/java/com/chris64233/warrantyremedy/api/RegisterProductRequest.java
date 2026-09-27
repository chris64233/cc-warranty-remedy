package com.chris64233.warrantyremedy.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

public record RegisterProductRequest(
        @NotBlank String serialNumber,
        @NotNull @PastOrPresent LocalDate saleDate,
        @NotNull @Min(1) Integer warrantyMonths) {
}
