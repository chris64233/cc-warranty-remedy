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

/**
 * 替换品库存。换货处置通过条件更新原子锁定可用替换品，
 * 避免并发下同一替换品被多笔处置占用。
 */
@Entity
@Table(name = "replacement_unit",
        uniqueConstraints = @UniqueConstraint(name = "uk_replacement_serial", columnNames = "serial_number"))
public class ReplacementUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReplacementUnitStatus status = ReplacementUnitStatus.AVAILABLE;

    /** 当前锁定该替换品的处置。 */
    @Column(name = "locked_by_remedy_id")
    private Long lockedByRemedyId;

    protected ReplacementUnit() {
    }

    public ReplacementUnit(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public Long getId() {
        return id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public ReplacementUnitStatus getStatus() {
        return status;
    }

    public void setStatus(ReplacementUnitStatus status) {
        this.status = status;
    }

    public Long getLockedByRemedyId() {
        return lockedByRemedyId;
    }

    public void setLockedByRemedyId(Long lockedByRemedyId) {
        this.lockedByRemedyId = lockedByRemedyId;
    }
}
