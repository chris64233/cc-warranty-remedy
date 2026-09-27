package com.chris64233.warrantyremedy.web;

import com.chris64233.warrantyremedy.api.RegisterReplacementUnitRequest;
import com.chris64233.warrantyremedy.api.ReplacementUnitView;
import com.chris64233.warrantyremedy.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/replacement-units")
public class ReplacementUnitController {

    private final ProductService productService;

    public ReplacementUnitController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReplacementUnitView register(@Valid @RequestBody RegisterReplacementUnitRequest request) {
        return productService.registerReplacementUnit(request);
    }

    @GetMapping
    public List<ReplacementUnitView> list() {
        return productService.listReplacementUnits();
    }
}
