package com.chris64233.warrantyremedy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 序列化产品实例。
 *
 * <p>记录序列号、销售日期、保修期限（月）及状态。换货发生时，新产品继承原产品的
 * {@code saleDate} 与 {@code warrantyMonths}，并通过 {@link #replacedFrom} /
 * {@link #replacedBy} 串起保修链；被替换的原产品进入 {@link ProductStatus#REPLACED}。
 * 退款后产品进入 {@link ProductStatus#REFUNDED}，保修资格终止。
 */
@Entity
@Table(name = "product",
        uniqueConstraints = {
                @jakarta.persistence.UniqueConstraint(name = "uk_product_serial", columnNames = "serial_number")
        },
        indexes = {
                @Index(name = "idx_product_status", columnList = "status")
        })
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 序列号，业务唯一。 */
    @Column(name = "serial_number", nullable = false, length = 64)
    private String serialNumber;

    /** 销售日期，保修期限自该日期起算；换货后继产品继承该日期；库存替换品尚未销售，为 null。 */
    @Column(name = "sale_date")
    private LocalDate saleDate;

    /** 保修期限（月）。 */
    @Column(name = "warranty_months", nullable = false)
    private Integer warrantyMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProductStatus status;

    /** 换货链前驱：本序列号替换了哪台产品。 */
    @OneToOne
    @JoinColumn(name = "replaced_from_id", unique = true)
    private Product replacedFrom;

    /** 换货链后继：本序列号被哪台产品替换。 */
    @OneToOne(mappedBy = "replacedFrom")
    private Product replacedBy;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Product() {
    }

    /** 注册一台正常销售、立即享有保修资格的产品。 */
    public static Product registered(String serialNumber, LocalDate saleDate, Integer warrantyMonths,
                                     LocalDateTime now) {
        Product p = new Product();
        p.serialNumber = serialNumber;
        p.saleDate = saleDate;
        p.warrantyMonths = warrantyMonths;
        p.status = ProductStatus.ACTIVE;
        p.createdAt = now;
        return p;
    }

    /** 登记一台换货库存替换品（不享受独立保修资格，等待被换货占用）。 */
    public static Product stockUnit(String serialNumber, Integer warrantyMonths, LocalDateTime now) {
        Product p = new Product();
        p.serialNumber = serialNumber;
        p.saleDate = null;
        p.warrantyMonths = warrantyMonths;
        p.status = ProductStatus.IN_STOCK;
        p.createdAt = now;
        return p;
    }

    /** 保修到期日 = 销售日期 + 保修月数；库存替换品尚无保修关系，返回 null。 */
    public LocalDate warrantyEndDate() {
        return saleDate == null ? null : saleDate.plusMonths(warrantyMonths);
    }

    /** 在给定日期是否仍处于保修期内。 */
    public boolean underWarrantyOn(LocalDate date) {
        LocalDate end = warrantyEndDate();
        return end != null && !date.isAfter(end);
    }

    public boolean hasStatus(ProductStatus expected) {
        return status == expected;
    }

    /** 执行退款：保修资格终止。 */
    public void markRefunded() {
        this.status = ProductStatus.REFUNDED;
    }

    /**
     * 换货执行：以本库存产品为替换品，承接 {@code original} 的保修关系。
     * 继承原销售日期与保修期限，建立双向换货链。
     *
     * <p>参数可能是 Hibernate 代理，读取其状态必须通过访问器（直接字段访问会读到代理壳的
     * 默认值），因此这里统一使用 getter。
     */
    public void inheritWarrantyFrom(Product original) {
        LocalDate originalSaleDate = original.getSaleDate();
        Integer originalWarrantyMonths = original.getWarrantyMonths();
        this.saleDate = originalSaleDate;
        this.warrantyMonths = originalWarrantyMonths;
        this.status = ProductStatus.ACTIVE;
        this.replacedFrom = original;
        // 必须为 public：original 可能是 Hibernate 代理，private 方法不会被代理转发到目标实体
        original.markReplacedBy(this);
    }

    /** 由后继替换品调用：原产品进入 REPLACED 并回指后继。 */
    public void markReplacedBy(Product successor) {
        this.status = ProductStatus.REPLACED;
        this.replacedBy = successor;
    }

    public Long getId() {
        return id;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public LocalDate getSaleDate() {
        return saleDate;
    }

    public Integer getWarrantyMonths() {
        return warrantyMonths;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Product getReplacedFrom() {
        return replacedFrom;
    }

    public Product getReplacedBy() {
        return replacedBy;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
