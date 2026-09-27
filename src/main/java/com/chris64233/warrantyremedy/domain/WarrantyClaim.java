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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 保修申请。包含故障现象、购买凭证与外部申请号；
 * 外部申请号全局唯一，用于幂等提交。申请一旦创建不可修改。
 */
@Entity
@Table(name = "warranty_claim",
        uniqueConstraints = @UniqueConstraint(name = "uk_claim_external_no", columnNames = "external_claim_no"))
public class WarrantyClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 外部申请号，幂等键。 */
    @Column(name = "external_claim_no", nullable = false, length = 64)
    private String externalClaimNo;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductUnit product;

    @Column(name = "fault_code", nullable = false, length = 64)
    private String faultCode;

    @Column(name = "fault_description", nullable = false, length = 2000)
    private String faultDescription;

    @Column(name = "proof_of_purchase", nullable = false, length = 2000)
    private String proofOfPurchase;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ClaimStatus status = ClaimStatus.OPEN;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WarrantyClaim() {
    }

    public WarrantyClaim(ProductUnit product, String faultCode, String faultDescription,
                         String proofOfPurchase, String externalClaimNo) {
        this.product = product;
        this.faultCode = faultCode;
        this.faultDescription = faultDescription;
        this.proofOfPurchase = proofOfPurchase;
        this.externalClaimNo = externalClaimNo;
    }

    public Long getId() {
        return id;
    }

    public String getExternalClaimNo() {
        return externalClaimNo;
    }

    public ProductUnit getProduct() {
        return product;
    }

    public String getFaultCode() {
        return faultCode;
    }

    public String getFaultDescription() {
        return faultDescription;
    }

    public String getProofOfPurchase() {
        return proofOfPurchase;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public void setStatus(ClaimStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
