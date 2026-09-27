package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.domain.RemedyCorrection;

import java.time.Instant;

public record CorrectionView(Long id, Long remedyId, String note, Instant createdAt) {

    public static CorrectionView of(RemedyCorrection correction) {
        return new CorrectionView(
                correction.getId(),
                correction.getRemedy().getId(),
                correction.getNote(),
                correction.getCreatedAt());
    }
}
