package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.domain.ReplacementUnit;
import com.chris64233.warrantyremedy.domain.ReplacementUnitStatus;

public record ReplacementUnitView(
        Long id,
        String serialNumber,
        ReplacementUnitStatus status,
        Long lockedByRemedyId) {

    public static ReplacementUnitView of(ReplacementUnit unit) {
        return new ReplacementUnitView(
                unit.getId(),
                unit.getSerialNumber(),
                unit.getStatus(),
                unit.getLockedByRemedyId());
    }
}
