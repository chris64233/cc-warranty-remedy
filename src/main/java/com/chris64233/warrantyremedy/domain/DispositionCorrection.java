package com.chris64233.warrantyremedy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 已执行处置的纠正记录。已执行处置不可修改或撤销，只能追加纠正（如维修返工、退款差额、
 * 换货补救）。纠正记录本身也不可修改、不可删除。
 */
@Entity
@Table(name = "disposition_correction", indexes = {
        @jakarta.persistence.Index(name = "idx_correction_disposition", columnList = "disposition_id")
})
public class DispositionCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "disposition_id")
    private Disposition disposition;

    /** 纠正类型，自由代码，如 REWORK / ADDITIONAL_REFUND / GOODWILL_REPLACEMENT。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "correction_type", nullable = false, length = 32)
    private CorrectionType type;

    @Column(name = "detail", nullable = false, length = 1024)
    private String detail;

    /** 纠正涉及的金额（分），可为空。 */
    @Column(name = "amount_cents")
    private Long amountCents;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    protected DispositionCorrection() {
    }

    public DispositionCorrection(CorrectionType type, String detail, Long amountCents, LocalDateTime now) {
        this.type = type;
        this.detail = detail;
        this.amountCents = amountCents;
        this.recordedAt = now;
    }

    void bindTo(Disposition disposition) {
        this.disposition = disposition;
    }

    public Long getId() {
        return id;
    }

    public CorrectionType getType() {
        return type;
    }

    public String getDetail() {
        return detail;
    }

    public Long getAmountCents() {
        return amountCents;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }
}
