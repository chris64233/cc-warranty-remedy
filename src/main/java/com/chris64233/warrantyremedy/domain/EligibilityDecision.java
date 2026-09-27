package com.chris64233.warrantyremedy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 保修资格判断结果。随申请提交时生成一次，此后不可修改。
 */
@Entity
@Table(name = "eligibility_decision",
        uniqueConstraints = @UniqueConstraint(name = "uk_eligibility_claim", columnNames = "claim_id"))
public class EligibilityDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false)
    private WarrantyClaim claim;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "reason", nullable = false, length = 512)
    private String reason;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt = Instant.now();

    protected EligibilityDecision() {
    }

    public EligibilityDecision(WarrantyClaim claim, boolean eligible, String reason) {
        this.claim = claim;
        this.eligible = eligible;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public WarrantyClaim getClaim() {
        return claim;
    }

    public boolean isEligible() {
        return eligible;
    }

    public String getReason() {
        return reason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
