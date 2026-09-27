package com.chris64233.warrantyremedy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 已执行处置的纠正记录，只允许追加，不允许修改或删除。
 */
@Entity
@Table(name = "remedy_correction")
public class RemedyCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "remedy_id", nullable = false)
    private Remedy remedy;

    @Column(name = "note", nullable = false, length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RemedyCorrection() {
    }

    public RemedyCorrection(Remedy remedy, String note) {
        this.remedy = remedy;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Remedy getRemedy() {
        return remedy;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
