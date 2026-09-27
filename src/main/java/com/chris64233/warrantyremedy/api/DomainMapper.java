package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.api.Views.ClaimView;
import com.chris64233.warrantyremedy.api.Views.CorrectionView;
import com.chris64233.warrantyremedy.api.Views.DispositionView;
import com.chris64233.warrantyremedy.api.Views.EligibilityView;
import com.chris64233.warrantyremedy.api.Views.EvidenceView;
import com.chris64233.warrantyremedy.api.Views.ProductView;
import com.chris64233.warrantyremedy.domain.ClaimEvidence;
import com.chris64233.warrantyremedy.domain.Disposition;
import com.chris64233.warrantyremedy.domain.DispositionCorrection;
import com.chris64233.warrantyremedy.domain.EligibilityDecision;
import com.chris64233.warrantyremedy.domain.Product;
import com.chris64233.warrantyremedy.domain.WarrantyClaim;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 领域实体 -> API 视图装配。必须在事务/打开的会话内调用（遍历懒加载关联）。
 */
@Component
public class DomainMapper {

    public ProductView productView(Product p) {
        Product from = p.getReplacedFrom();
        Product to = p.getReplacedBy();
        return new ProductView(
                p.getId(),
                p.getSerialNumber(),
                p.getSaleDate(),
                p.getWarrantyMonths(),
                p.warrantyEndDate(),
                p.getStatus().name(),
                from == null ? null : from.getSerialNumber(),
                to == null ? null : to.getSerialNumber(),
                p.getCreatedAt());
    }

    public ClaimView claimView(WarrantyClaim c) {
        List<EvidenceView> evidences = c.getEvidences().stream().map(this::evidenceView).toList();
        Disposition d = c.getDisposition();
        return new ClaimView(
                c.getId(),
                c.getExternalRef(),
                c.getProduct().getSerialNumber(),
                c.getSymptom(),
                c.getStatus().name(),
                c.getSubmittedAt(),
                c.getApprovedAt(),
                c.getExecutedAt(),
                c.getCancelledAt(),
                c.getCancelReason(),
                eligibilityView(c.getEligibility()),
                evidences,
                d == null ? null : dispositionView(d));
    }

    public EvidenceView evidenceView(ClaimEvidence e) {
        return new EvidenceView(e.getId(), e.getType().name(), e.getReference(), e.getNote());
    }

    public EligibilityView eligibilityView(EligibilityDecision d) {
        if (d == null) {
            return null;
        }
        return new EligibilityView(d.isEligible(), d.getEvaluatedOn(), d.getWarrantyEnd(),
                d.getReason(), d.getNote());
    }

    public DispositionView dispositionView(Disposition d) {
        List<CorrectionView> corrections = d.getCorrections().stream()
                .map(this::correctionView).toList();
        Product replacement = d.getReplacement();
        return new DispositionView(
                d.getId(),
                d.getType().name(),
                d.getStatus().name(),
                replacement == null ? null : replacement.getSerialNumber(),
                d.getRefundAmountCents(),
                d.getNote(),
                d.getDecidedAt(),
                d.getExecutedAt(),
                d.getCancelledAt(),
                corrections);
    }

    public CorrectionView correctionView(DispositionCorrection c) {
        return new CorrectionView(c.getId(), c.getType().name(), c.getDetail(),
                c.getAmountCents(), c.getRecordedAt());
    }
}
