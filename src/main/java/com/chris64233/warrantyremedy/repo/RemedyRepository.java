package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.Remedy;
import com.chris64233.warrantyremedy.domain.RemedyStatus;
import com.chris64233.warrantyremedy.domain.RemedyType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RemedyRepository extends JpaRepository<Remedy, Long> {

    Optional<Remedy> findByClaim_IdAndStatusIn(Long claimId, Collection<RemedyStatus> statuses);

    List<Remedy> findByProductIdOrderByDecidedAtAsc(Long productId);

    /** 悲观写锁：执行/撤销处置时串行化。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Remedy> findWithLockById(Long id);

    /** 同一产品同一故障是否已有执行完成的维修（决定再次故障时升级为换货/退款）。 */
    @Query("select case when count(r) > 0 then true else false end from Remedy r "
            + "where r.productId = :productId and r.type = :type and r.status = :status "
            + "and r.claim.faultCode = :faultCode")
    boolean existsByProductIdAndTypeAndStatusAndFaultCode(@Param("productId") Long productId,
                                                          @Param("type") RemedyType type,
                                                          @Param("status") RemedyStatus status,
                                                          @Param("faultCode") String faultCode);
}
