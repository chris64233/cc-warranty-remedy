package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.domain.ProductStatus;
import com.chris64233.warrantyremedy.domain.ProductUnit;

import java.time.Instant;
import java.time.LocalDate;

public record ProductView(
        Long id,
        String serialNumber,
        LocalDate saleDate,
        int warrantyMonths,
        LocalDate warrantyEndDate,
        ProductStatus status,
        boolean eligibleNow,
        Long originUnitId,
        Long replacedByUnitId,
        Instant createdAt) {

    public static ProductView of(ProductUnit unit, LocalDate today) {
        return new ProductView(
                unit.getId(),
                unit.getSerialNumber(),
                unit.getSaleDate(),
                unit.getWarrantyMonths(),
                unit.warrantyEndDate(),
                unit.getStatus(),
                unit.warrantyActiveOn(today),
                unit.getOriginUnitId(),
                unit.getReplacedByUnitId(),
                unit.getCreatedAt());
    }
}
