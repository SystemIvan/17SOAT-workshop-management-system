package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "stock_items")
public class StockItemJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String sku;
    @Column(nullable = false, length = 255)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockItemType type;
    @Column(name = "price_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceValue;
    @Column(name = "price_currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String priceCurrency;
    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "minimum_quantity")
    private Integer minimumQuantity;
    @Column(name = "target_quantity")
    private Integer targetQuantity;

    protected StockItemJpaEntity() {
    }

    public StockItemJpaEntity(UUID id, String sku, String name, StockItemType type, BigDecimal priceValue,
                              String priceCurrency, int availableQuantity, boolean active, Integer minimumQuantity,
                              Integer targetQuantity) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.type = type;
        this.priceValue = priceValue;
        this.priceCurrency = priceCurrency;
        this.availableQuantity = availableQuantity;
        this.active = active;
        this.minimumQuantity = minimumQuantity;
        this.targetQuantity = targetQuantity;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public StockItemType getType() { return type; }
    public BigDecimal getPriceValue() { return priceValue; }
    public String getPriceCurrency() { return priceCurrency; }
    public int getAvailableQuantity() { return availableQuantity; }
    public boolean isActive() { return active; }
    public Integer getMinimumQuantity() { return minimumQuantity; }
    public Integer getTargetQuantity() { return targetQuantity; }
}
