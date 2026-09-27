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

import java.time.Instant;

/**
 * 处置决定（维修/换货/退款）。决定内容（类型、关联申请）创建后不可修改；
 * 状态随执行/撤销流转，已执行后只能通过 {@link RemedyCorrection} 追加纠正。
 */
@Entity
@Table(name = "remedy")
public class Remedy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private WarrantyClaim claim;

    /** 冗余产品主键，便于按产品查询历史处置。 */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "remedy_type", nullable = false, length = 16)
    private RemedyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "remedy_status", nullable = false, length = 16)
    private RemedyStatus status = RemedyStatus.PENDING;

    /** 换货处置锁定的替换品。 */
    @Column(name = "replacement_unit_id")
    private Long replacementUnitId;

    /** 换货执行后生成的新产品实例。 */
    @Column(name = "new_product_unit_id")
    private Long newProductUnitId;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Remedy() {
    }

    public Remedy(WarrantyClaim claim, Long productId, RemedyType type) {
        this.claim = claim;
        this.productId = productId;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public WarrantyClaim getClaim() {
        return claim;
    }

    public Long getProductId() {
        return productId;
    }

    public RemedyType getType() {
        return type;
    }

    public void setType(RemedyType type) {
        this.type = type;
    }

    public RemedyStatus getStatus() {
        return status;
    }

    public void setStatus(RemedyStatus status) {
        this.status = status;
    }

    public Long getReplacementUnitId() {
        return replacementUnitId;
    }

    public void setReplacementUnitId(Long replacementUnitId) {
        this.replacementUnitId = replacementUnitId;
    }

    public Long getNewProductUnitId() {
        return newProductUnitId;
    }

    public void setNewProductUnitId(Long newProductUnitId) {
        this.newProductUnitId = newProductUnitId;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(Instant cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
