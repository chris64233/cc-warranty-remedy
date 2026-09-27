package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.EligibilityDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EligibilityDecisionRepository extends JpaRepository<EligibilityDecision, Long> {

    Optional<EligibilityDecision> findByClaim_Id(Long claimId);
}
