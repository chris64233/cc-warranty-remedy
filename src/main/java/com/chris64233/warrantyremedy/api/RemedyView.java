package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.domain.RemedyStatus;
import com.chris64233.warrantyremedy.domain.RemedyType;

import java.time.Instant;
import java.util.List;

/** 处置记录视图，包含换货的替换关系与已追加的纠正记录。 */
public record RemedyView(
        Long id,
        Long claimId,
        Long productId,
        RemedyType type,
        RemedyStatus status,
        Long replacementUnitId,
        String replacementSerialNumber,
        Long newProductUnitId,
        String newSerialNumber,
        List<CorrectionView> corrections,
        Instant decidedAt,
        Instant executedAt,
        Instant cancelledAt) {
}
