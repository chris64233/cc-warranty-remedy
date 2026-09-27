package com.chris64233.warrantyremedy.service;

import com.chris64233.warrantyremedy.api.DomainMapper;
import com.chris64233.warrantyremedy.api.Views.ProductView;
import com.chris64233.warrantyremedy.api.Views.WarrantyChainView;
import com.chris64233.warrantyremedy.domain.Product;
import com.chris64233.warrantyremedy.domain.ProductStatus;
import com.chris64233.warrantyremedy.repo.ProductRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 序列化产品与换货库存管理、保修链查询。
 */
@Service
public class ProductService {

    private final ProductRepository products;
    private final DomainMapper mapper;
    private final Clock clock;

    public ProductService(ProductRepository products, DomainMapper mapper, Clock clock) {
        this.products = products;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public ProductView register(String serialNumber, LocalDate saleDate, Integer warrantyMonths) {
        if (products.existsBySerialNumber(serialNumber)) {
            throw new BusinessRuleException("序列号已存在：" + serialNumber);
        }
        if (warrantyMonths == null || warrantyMonths <= 0) {
            throw new ValidationException("保修期限必须为正整数（月）");
        }
        if (saleDate == null) {
            throw new ValidationException("销售日期不能为空");
        }
        Product p = products.save(
                Product.registered(serialNumber, saleDate, warrantyMonths, LocalDateTime.now(clock)));
        return mapper.productView(p);
    }

    /** 登记换货库存替换品：无销售日期，保修期限将在换货时继承自原产品。 */
    @Transactional
    public ProductView registerStockUnit(String serialNumber, Integer warrantyMonths) {
        if (products.existsBySerialNumber(serialNumber)) {
            throw new BusinessRuleException("序列号已存在：" + serialNumber);
        }
        if (warrantyMonths == null || warrantyMonths <= 0) {
            throw new ValidationException("保修期限必须为正整数（月）");
        }
        Product p = products.save(Product.stockUnit(serialNumber, warrantyMonths, LocalDateTime.now(clock)));
        return mapper.productView(p);
    }

    @Transactional(readOnly = true)
    public ProductView getBySerial(String serialNumber) {
        return mapper.productView(requireBySerial(serialNumber));
    }

    @Transactional(readOnly = true)
    public List<ProductView> listStockUnits() {
        return products.findByStatus(ProductStatus.IN_STOCK).stream().map(mapper::productView).toList();
    }

    /**
     * 保修链：从该序列号沿 replacedFrom 追溯到最初购买的产品，再沿 replacedBy 前进到当前
     * 有效序列号，返回从源头到当前的完整有序节点。
     */
    @Transactional(readOnly = true)
    public WarrantyChainView warrantyChain(String serialNumber) {
        Product start = requireBySerial(serialNumber);
        Product cur = start;
        while (cur.getReplacedFrom() != null) {
            cur = cur.getReplacedFrom();
        }
        List<Product> chain = new ArrayList<>();
        chain.add(cur);
        Set<Long> guard = new HashSet<>();
        guard.add(cur.getId());
        while (cur.getReplacedBy() != null) {
            cur = cur.getReplacedBy();
            if (!guard.add(cur.getId())) {
                throw new IllegalStateException("换货链出现环路，序列号：" + serialNumber);
            }
            chain.add(cur);
        }
        List<ProductView> views = chain.stream().map(mapper::productView).toList();
        return new WarrantyChainView(start.getSerialNumber(), views,
                views.get(views.size() - 1).serialNumber());
    }

    @Transactional(readOnly = true)
    public Product requireBySerial(String serialNumber) {
        return products.findBySerialNumber(serialNumber)
                .orElseThrow(() -> new ResourceNotFoundException("产品不存在：" + serialNumber));
    }
}
