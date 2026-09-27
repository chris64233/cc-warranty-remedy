package com.chris64233.warrantyremedy.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 处置决定（维修 / 换货 / 退款）。
 *
 * <p>决定本身（类型、选定的替换品、理由）批准后不可修改；状态只允许沿
 * {@code APPROVED -> EXECUTED} 推进，或随申请撤销变为 {@code CANCELLED}。
 * 换货批准时 {@code replacement} 已被原子占用；执行时保修关系转移到新序列号。
 * 已执行处置不可修改或撤销，只能通过 {@link DispositionCorrection} 追加纠正记录。
 */
@Entity
@Table(name = "disposition",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_disposition_claim", columnNames = "claim_id")
        })
public class Disposition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", unique = true)
    private WarrantyClaim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "disposition_type", nullable = false, length = 16)
    private DispositionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DispositionStatus status;

    /** 换货时选定的替换品（批准即占用）；维修/退款为 null。 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replacement_id")
    private Product replacement;

    /** 退款金额（分），null 表示未登记金额。 */
    @Column(name = "refund_amount_cents")
    private Long refundAmountCents;

    @Column(name = "decision_note", length = 512)
    private String note;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;

    private LocalDateTime executedAt;

    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "disposition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "position")
    private List<DispositionCorrection> corrections = new ArrayList<>();

    protected Disposition() {
    }

    public static Disposition approved(WarrantyClaim claim, DispositionType type, Product replacement,
                                       Long refundAmountCents, String note, LocalDateTime now) {
        Disposition d = new Disposition();
        d.claim = claim;
        d.type = type;
        d.status = DispositionStatus.APPROVED;
        d.replacement = replacement;
        d.refundAmountCents = refundAmountCents;
        d.note = note;
        d.decidedAt = now;
        return d;
    }

    public void markExecuted(LocalDateTime now) {
        if (status != DispositionStatus.APPROVED) {
            throw new IllegalStateException("处置当前状态为 " + status + "，无法执行");
        }
        this.status = DispositionStatus.EXECUTED;
        this.executedAt = now;
    }

    public void markCancelled(LocalDateTime now) {
        if (status != DispositionStatus.APPROVED) {
            throw new IllegalStateException("处置当前状态为 " + status + "，无法撤销");
        }
        this.status = DispositionStatus.CANCELLED;
        this.cancelledAt = now;
    }

    public void addCorrection(DispositionCorrection correction) {
        correction.bindTo(this);
        this.corrections.add(correction);
    }

    public Long getId() {
        return id;
    }

    public WarrantyClaim getClaim() {
        return claim;
    }

    public DispositionType getType() {
        return type;
    }

    public DispositionStatus getStatus() {
        return status;
    }

    public Product getReplacement() {
        return replacement;
    }

    public Long getRefundAmountCents() {
        return refundAmountCents;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public List<DispositionCorrection> getCorrections() {
        return corrections;
    }
}
