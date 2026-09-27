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

/**
 * 申请证据（购买凭证、故障影像等）。随申请提交写入，之后只能追加，不能修改或删除。
 */
@Entity
@Table(name = "claim_evidence", indexes = {
        @jakarta.persistence.Index(name = "idx_evidence_claim", columnList = "claim_id")
})
public class ClaimEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private WarrantyClaim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 24)
    private EvidenceType type;

    /** 文件名 / 对象存储 key / 外部链接，至少包含一份购买凭证。 */
    @Column(name = "reference", nullable = false, length = 512)
    private String reference;

    @Column(length = 256)
    private String note;

    protected ClaimEvidence() {
    }

    public ClaimEvidence(EvidenceType type, String reference, String note) {
        this.type = type;
        this.reference = reference;
        this.note = note;
    }

    void bindTo(WarrantyClaim claim) {
        this.claim = claim;
    }

    public Long getId() {
        return id;
    }

    public EvidenceType getType() {
        return type;
    }

    public String getReference() {
        return reference;
    }

    public String getNote() {
        return note;
    }
}
