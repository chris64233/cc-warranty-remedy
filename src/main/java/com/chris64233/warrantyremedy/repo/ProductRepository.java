package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.Product;
import com.chris64233.warrantyremedy.domain.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySerialNumber(String serialNumber);

    boolean existsBySerialNumber(String serialNumber);

    /** 仅取 id 的轻量查询，配合条件更新占用替换品，避免持入过期托管态。 */
    @Query("select p.id from Product p where p.serialNumber = :serial")
    Optional<Long> findIdBySerialNumber(@Param("serial") String serial);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    List<Product> findByStatus(ProductStatus status);

    /**
     * 原子占用替换品：仅当替换品仍在库存（{@code IN_STOCK}）时将其置为 HELD。
     * 数据库行级条件更新，并发下至多一个事务影响 1 行。
     *
     * @return 更新行数；0 表示已被其他申请占用或不存在/不在库存
     */
    @Modifying
    @Query("update Product p set p.status = com.chris64233.warrantyremedy.domain.ProductStatus.HELD "
            + "where p.id = :id and p.status = com.chris64233.warrantyremedy.domain.ProductStatus.IN_STOCK")
    int holdIfInStock(@Param("id") Long id);

    /** 撤销已批准换货时释放替换品（HELD -> IN_STOCK）。 */
    @Modifying
    @Query("update Product p set p.status = com.chris64233.warrantyremedy.domain.ProductStatus.IN_STOCK "
            + "where p.id = :id and p.status = com.chris64233.warrantyremedy.domain.ProductStatus.HELD")
    int releaseIfHeld(@Param("id") Long id);
}
