package com.chris64233.warrantyremedy.api;

import com.chris64233.warrantyremedy.api.Views.ProductView;
import com.chris64233.warrantyremedy.api.Views.WarrantyChainView;
import com.chris64233.warrantyremedy.service.ProductService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 序列化产品与换货库存：登记、查询、保修链。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    public record RegisterProductRequest(
            @NotBlank String serialNumber,
            @NotNull LocalDate saleDate,
            @NotNull @Positive Integer warrantyMonths) {
    }

    /** 登记换货库存替换品（无销售日期；换货执行时继承原产品保修关系）。 */
    public record RegisterStockRequest(
            @NotBlank String serialNumber,
            @NotNull @Positive Integer warrantyMonths) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView register(@jakarta.validation.Valid @RequestBody RegisterProductRequest req) {
        return productService.register(req.serialNumber(), req.saleDate(), req.warrantyMonths());
    }

    @PostMapping("/stock-units")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView registerStockUnit(@jakarta.validation.Valid @RequestBody RegisterStockRequest req) {
        return productService.registerStockUnit(req.serialNumber(), req.warrantyMonths());
    }

    @GetMapping("/stock-units")
    public List<ProductView> stockUnits() {
        return productService.listStockUnits();
    }

    @GetMapping("/{serial}")
    public ProductView get(@PathVariable String serial) {
        return productService.getBySerial(serial);
    }

    /** 产品保修链：源头序列号 -> ... -> 当前有效序列号，以及状态、销售日期、保修到期日。 */
    @GetMapping("/{serial}/warranty-chain")
    public WarrantyChainView warrantyChain(@PathVariable String serial) {
        return productService.warrantyChain(serial);
    }
}
