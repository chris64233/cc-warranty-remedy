package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.ClaimStatus;
import com.chris64233.warrantyremedy.domain.WarrantyClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.Optional;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {

    Optional<WarrantyClaim> findByExternalClaimNo(String externalClaimNo);

    boolean existsByProduct_IdAndFaultCodeAndStatusIn(Long productId, String faultCode,
                                                      Collection<ClaimStatus> statuses);

    /** 悲观写锁：并发批准同一申请时串行化。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WarrantyClaim> findWithLockById(Long id);
}
