package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.ReplacementUnit;
import com.chris64233.warrantyremedy.domain.ReplacementUnitStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReplacementUnitRepository extends JpaRepository<ReplacementUnit, Long> {

    boolean existsBySerialNumber(String serialNumber);

    List<ReplacementUnit> findByStatusOrderByIdAsc(ReplacementUnitStatus status);

    /**
     * 原子锁定：仅当替换品仍为可用时占用，返回受影响行数。
     * 并发占用同一替换品时只有一个事务得到 1。
     */
    @Modifying
    @Query("update ReplacementUnit u set u.status = com.chris64233.warrantyremedy.domain.ReplacementUnitStatus.LOCKED, "
            + "u.lockedByRemedyId = :remedyId where u.id = :unitId and u.status = com.chris64233.warrantyremedy.domain.ReplacementUnitStatus.AVAILABLE")
    int lockIfAvailable(@Param("unitId") Long unitId, @Param("remedyId") Long remedyId);

    /** 释放指定处置占用的替换品（撤销时调用）。 */
    @Modifying
    @Query("update ReplacementUnit u set u.status = com.chris64233.warrantyremedy.domain.ReplacementUnitStatus.AVAILABLE, "
            + "u.lockedByRemedyId = null where u.id = :unitId and u.lockedByRemedyId = :remedyId "
            + "and u.status = com.chris64233.warrantyremedy.domain.ReplacementUnitStatus.LOCKED")
    int releaseIfLockedBy(@Param("unitId") Long unitId, @Param("remedyId") Long remedyId);
}
