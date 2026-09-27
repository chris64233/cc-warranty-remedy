package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.WarrantyClaim;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {

    Optional<WarrantyClaim> findByExternalRef(String externalRef);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WarrantyClaim c where c.id = :id")
    Optional<WarrantyClaim> findByIdForUpdate(@Param("id") Long id);

    /** 申请详情：一次抓取证据、资格判断、处置（含替换品与纠正记录）。 */
    @EntityGraph(attributePaths = {"product", "evidences", "eligibility", "disposition",
            "disposition.replacement", "disposition.corrections"})
    @Query("select c from WarrantyClaim c where c.externalRef = :ref")
    Optional<WarrantyClaim> findDetailByExternalRef(@Param("ref") String ref);
}
