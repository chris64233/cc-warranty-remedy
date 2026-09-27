package com.chris64233.warrantyremedy.repo;

import com.chris64233.warrantyremedy.domain.Disposition;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositionRepository extends JpaRepository<Disposition, Long> {

    /**
     * 查询一台产品的处置记录：申请产品与换货替换品任一命中，按决定时间升序，
     * 同时抓取纠正记录与替换品。
     */
    @EntityGraph(attributePaths = {"claim", "claim.product", "replacement", "corrections"})
    @org.springframework.data.jpa.repository.Query(
            "select d from Disposition d where d.claim.product.id = :productId "
                    + "or d.replacement.id = :productId order by d.decidedAt asc")
    List<Disposition> findByProductId(@org.springframework.data.repository.query.Param("productId") Long productId);
}
