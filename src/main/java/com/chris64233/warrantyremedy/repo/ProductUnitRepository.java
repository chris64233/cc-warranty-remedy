package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.ProductUnit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProductUnitRepository extends JpaRepository<ProductUnit, Long> {

    Optional<ProductUnit> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);

    /** 悲观写锁：用于串行化同一产品上的申请提交与处置流转。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductUnit> findWithLockById(Long id);
}
