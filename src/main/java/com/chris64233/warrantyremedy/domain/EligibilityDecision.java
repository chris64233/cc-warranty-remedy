package com.chris64233.warrantyremedy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 资格判断记录（受理时固化，不可修改）。
 *
 * <p>记录判断基准日、保修到期日及判定理由；一份申请只有一条、永不修改、不删除。
 */
@Entity
@Table(name = "eligibility_decision")
public class EligibilityDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "eval_date", nullable = false)
    private LocalDate evaluatedOn;

    @Column(name = "warranty_end")
    private LocalDate warrantyEnd;

    /** 不符合时的原因码；符合时为 null。 */
    @Column(name = "ineligibility_reason", length = 128)
    private String reason;

    @Column(name = "eligibility_note", length = 512)
    private String note;

    protected EligibilityDecision() {
    }

    public EligibilityDecision(boolean eligible, LocalDate evaluatedOn, LocalDate warrantyEnd,
                               String reason, String note) {
        this.eligible = eligible;
        this.evaluatedOn = evaluatedOn;
        this.warrantyEnd = warrantyEnd;
        this.reason = reason;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public boolean isEligible() {
        return eligible;
    }

    public LocalDate getEvaluatedOn() {
        return evaluatedOn;
    }

    public LocalDate getWarrantyEnd() {
        return warrantyEnd;
    }

    public String getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }
}
