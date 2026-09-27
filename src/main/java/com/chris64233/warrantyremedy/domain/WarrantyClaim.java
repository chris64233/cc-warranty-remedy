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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 保修申请。
 *
 * <p>不可变内容：外部申请号 {@code externalRef}、产品 {@code product}、故障现象 {@code symptom}
 * 及受理时固化的资格判断（{@link EligibilityDecision}）。这些字段创建后不再修改；
 * 处置通过独立的 {@link Disposition} / {@link DispositionCorrection} 记录追加。
 *
 * <p>幂等：{@code external_ref} 全局唯一，重复提交返回原申请。
 *
 * <p>同一产品同一故障只能有一笔活动申请：活动期（SUBMITTED/APPROVED）写入
 * {@code active_dedupe_key}（product|symptom 规范化），终态时置空；该列带唯一约束，
 * 数据库层面保证并发提交也不会出现两笔活动申请。
 */
@Entity
@Table(name = "warranty_claim",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_claim_external_ref", columnNames = "external_ref"),
                @UniqueConstraint(name = "uk_claim_active_dedupe", columnNames = "active_dedupe_key")
        },
        indexes = {
                @Index(name = "idx_claim_product", columnList = "product_id"),
                @Index(name = "idx_claim_status", columnList = "status")
        })
public class WarrantyClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部申请号，保证受理幂等，全局唯一。 */
    @Column(name = "external_ref", nullable = false, length = 64)
    private String externalRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 故障现象（原文，不可修改）。 */
    @Column(name = "symptom", nullable = false, length = 512)
    private String symptom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ClaimStatus status;

    /** 活动申请去重键（产品id|规范化故障 的 SHA-256 十六进制）；终态置空以放行同产品同故障的新申请。 */
    @Column(name = "active_dedupe_key", length = 64)
    private String activeDedupeKey;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "position")
    private List<ClaimEvidence> evidences = new ArrayList<>();

    /** 受理时固化的资格判断，不可修改。 */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "eligibility_id", unique = true)
    private EligibilityDecision eligibility;

    @OneToOne(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    private Disposition disposition;

    @Version
    private Long version;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    private LocalDateTime approvedAt;
    private LocalDateTime executedAt;
    private LocalDateTime cancelledAt;

    /** 撤销原因（仅 CANCELLED 时有值）。 */
    @Column(name = "cancel_reason", length = 512)
    private String cancelReason;

    protected WarrantyClaim() {
    }

    public static WarrantyClaim submit(String externalRef, Product product, String symptom,
                                       String activeDedupeKey, LocalDateTime now) {
        WarrantyClaim c = new WarrantyClaim();
        c.externalRef = externalRef;
        c.product = product;
        c.symptom = symptom;
        c.status = ClaimStatus.SUBMITTED;
        c.activeDedupeKey = activeDedupeKey;
        c.submittedAt = now;
        return c;
    }

    public void attachEligibility(EligibilityDecision decision) {
        this.eligibility = decision;
    }

    public void addEvidence(ClaimEvidence evidence) {
        evidence.bindTo(this);
        this.evidences.add(evidence);
    }

    public void attachDisposition(Disposition disposition) {
        this.disposition = disposition;
        this.status = ClaimStatus.APPROVED;
        this.approvedAt = disposition.getDecidedAt();
    }

    public void markExecuted(LocalDateTime now) {
        this.status = ClaimStatus.EXECUTED;
        this.activeDedupeKey = null;
        this.executedAt = now;
    }

    public void markCancelled(LocalDateTime now, String reason) {
        this.status = ClaimStatus.CANCELLED;
        this.activeDedupeKey = null;
        this.cancelledAt = now;
        this.cancelReason = reason;
    }

    public boolean isActive() {
        return status == ClaimStatus.SUBMITTED || status == ClaimStatus.APPROVED;
    }

    public Long getId() {
        return id;
    }

    public String getExternalRef() {
        return externalRef;
    }

    public Product getProduct() {
        return product;
    }

    public String getSymptom() {
        return symptom;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public String getActiveDedupeKey() {
        return activeDedupeKey;
    }

    public List<ClaimEvidence> getEvidences() {
        return evidences;
    }

    public EligibilityDecision getEligibility() {
        return eligibility;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
