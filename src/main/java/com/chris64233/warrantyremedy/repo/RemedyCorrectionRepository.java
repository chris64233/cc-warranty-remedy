package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.RemedyCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RemedyCorrectionRepository extends JpaRepository<RemedyCorrection, Long> {

    List<RemedyCorrection> findByRemedy_IdOrderByCreatedAtAsc(Long remedyId);
}
