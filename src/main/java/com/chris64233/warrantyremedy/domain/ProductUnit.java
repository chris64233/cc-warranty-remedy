package com.chris64233.warrantyremedy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 序列化产品实例。记录序列号、销售日期、保修期限与换货链关系；
 * 历史处置通过 {@link Remedy} 按产品查询。
 */
@Entity
@Table(name = "product_unit",
        uniqueConstraints = @UniqueConstraint(name = "uk_product_serial", columnNames = "serial_number"))
public class ProductUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(name = "warranty_months", nullable = false)
    private int warrantyMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProductStatus status = ProductStatus.ACTIVE;

    /** 换货链：本实例由哪个旧实例换货而来，可为空。 */
    @Column(name = "origin_unit_id")
    private Long originUnitId;

    /** 换货链：本实例被哪个新实例替换，可为空。 */
    @Column(name = "replaced_by_unit_id")
    private Long replacedByUnitId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ProductUnit() {
    }

    public ProductUnit(String serialNumber, LocalDate saleDate, int warrantyMonths) {
        this.serialNumber = serialNumber;
        this.saleDate = saleDate;
        this.warrantyMonths = warrantyMonths;
    }

    public LocalDate warrantyEndDate() {
        return saleDate.plusMonths(warrantyMonths);
    }

    /** 在指定日期是否具备保修资格：状态正常且未过保修期限。 */
    public boolean warrantyActiveOn(LocalDate date) {
        return status == ProductStatus.ACTIVE && !warrantyEndDate().isBefore(date);
    }

    public Long getId() {
        return id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Long getOriginUnitId() {
        return originUnitId;
    }

    public void setOriginUnitId(Long originUnitId) {
        this.originUnitId = originUnitId;
    }

    public Long getReplacedByUnitId() {
        return replacedByUnitId;
    }

    public void setReplacedByUnitId(Long replacedByUnitId) {
        this.replacedByUnitId = replacedByUnitId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
