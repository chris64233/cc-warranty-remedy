package com.chris64233.warrantyremedy.web;

import com.chris64233.warrantyremedy.api.ProductView;
import com.chris64233.warrantyremedy.api.RegisterProductRequest;
import com.chris64233.warrantyremedy.api.RemedyView;
import com.chris64233.warrantyremedy.api.WarrantyChainView;
import com.chris64233.warrantyremedy.service.ProductService;
import com.chris64233.warrantyremedy.service.RemedyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final RemedyService remedyService;

    public ProductController(ProductService productService, RemedyService remedyService) {
        this.productService = productService;
        this.remedyService = remedyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView register(@Valid @RequestBody RegisterProductRequest request) {
        return productService.register(request);
    }

    @GetMapping("/{id}")
    public ProductView get(@PathVariable long id) {
        return productService.get(id);
    }

    /** 产品保修链：从最初实例到当前实例的换货序列。 */
    @GetMapping("/{id}/warranty-chain")
    public WarrantyChainView warrantyChain(@PathVariable long id) {
        return productService.warrantyChain(id);
    }

    /** 产品的历史处置记录。 */
    @GetMapping("/{id}/remedies")
    public List<RemedyView> remedies(@PathVariable long id) {
        return remedyService.remediesOfProduct(id);
    }
}
