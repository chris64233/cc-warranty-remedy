package com.chris64233.warrantyremedy.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REST 出参视图，均为不可变 record，按 API 文档形态组装（不直接暴露 JPA 实体）。
 */
public final class Views {

    private Views() {
    }

    public record ProductView(
            Long id,
            String serialNumber,
            LocalDate saleDate,
            Integer warrantyMonths,
            LocalDate warrantyEnd,
            String status,
            String replacedFromSerial,
            String replacedBySerial,
            LocalDateTime createdAt) {
    }

    public record WarrantyChainView(String queriedSerial, List<ProductView> chain, String currentSerial) {
    }

    public record EvidenceView(Long id, String type, String reference, String note) {
    }

    public record EligibilityView(
            boolean eligible,
            LocalDate evaluatedOn,
            LocalDate warrantyEnd,
            String reason,
            String note) {
    }

    public record CorrectionView(
            Long id,
            String type,
            String detail,
            Long amountCents,
            LocalDateTime recordedAt) {
    }

    public record DispositionView(
            Long id,
            String type,
            String status,
            String replacementSerial,
            Long refundAmountCents,
            String note,
            LocalDateTime decidedAt,
            LocalDateTime executedAt,
            LocalDateTime cancelledAt,
            List<CorrectionView> corrections) {
    }

    public record ClaimView(
            Long id,
            String externalRef,
            String serialNumber,
            String symptom,
            String status,
            LocalDateTime submittedAt,
            LocalDateTime approvedAt,
            LocalDateTime executedAt,
            LocalDateTime cancelledAt,
            String cancelReason,
            EligibilityView eligibility,
            List<EvidenceView> evidences,
            DispositionView disposition) {
    }

    public record ErrorResponse(String error, String message, LocalDateTime timestamp) {
    }
}
