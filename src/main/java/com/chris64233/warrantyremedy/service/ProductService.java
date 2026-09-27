package com.chris64233.warrantyremedy.service;

import com.chris64233.warrantyremedy.api.ProductView;
import com.chris64233.warrantyremedy.api.RegisterProductRequest;
import com.chris64233.warrantyremedy.api.RegisterReplacementUnitRequest;
import com.chris64233.warrantyremedy.api.ReplacementUnitView;
import com.chris64233.warrantyremedy.api.WarrantyChainView;
import com.chris64233.warrantyremedy.domain.ProductUnit;
import com.chris64233.warrantyremedy.domain.ReplacementUnit;
import com.chris64233.warrantyremedy.repo.ProductUnitRepository;
import com.chris64233.warrantyremedy.repo.ReplacementUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {

    private final ProductUnitRepository productRepository;
    private final ReplacementUnitRepository replacementUnitRepository;

    public ProductService(ProductUnitRepository productRepository,
                          ReplacementUnitRepository replacementUnitRepository) {
        this.productRepository = productRepository;
        this.replacementUnitRepository = replacementUnitRepository;
    }

    @Transactional
    public ProductView register(RegisterProductRequest request) {
        if (productRepository.existsBySerialNumber(request.serialNumber())) {
            throw new ConflictException("序列号已存在: " + request.serialNumber());
        }
        ProductUnit unit = new ProductUnit(request.serialNumber(), request.saleDate(), request.warrantyMonths());
        return ProductView.of(productRepository.save(unit), LocalDate.now());
    }

    @Transactional(readOnly = true)
    public ProductView get(long productId) {
        return ProductView.of(findProduct(productId), LocalDate.now());
    }

    /**
     * 产品保修链：沿换货关系回溯到最初实例，再正向列出到最新实例。
     */
    @Transactional(readOnly = true)
    public WarrantyChainView warrantyChain(long productId) {
        ProductUnit root = findProduct(productId);
        while (root.getOriginUnitId() != null) {
            root = findProduct(root.getOriginUnitId());
        }
        List<ProductView> chain = new ArrayList<>();
        LocalDate today = LocalDate.now();
        ProductUnit current = root;
        while (true) {
            chain.add(ProductView.of(current, today));
            if (current.getReplacedByUnitId() == null) {
                break;
            }
            current = findProduct(current.getReplacedByUnitId());
        }
        return new WarrantyChainView(chain);
    }

    @Transactional
    public ReplacementUnitView registerReplacementUnit(RegisterReplacementUnitRequest request) {
        if (replacementUnitRepository.existsBySerialNumber(request.serialNumber())) {
            throw new ConflictException("替换品序列号已存在: " + request.serialNumber());
        }
        return ReplacementUnitView.of(replacementUnitRepository.save(new ReplacementUnit(request.serialNumber())));
    }

    @Transactional(readOnly = true)
    public List<ReplacementUnitView> listReplacementUnits() {
        return replacementUnitRepository.findAll().stream().map(ReplacementUnitView::of).toList();
    }

    private ProductUnit findProduct(long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("产品不存在: " + productId));
    }
}
