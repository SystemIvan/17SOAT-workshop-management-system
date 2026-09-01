package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.UUID;

@Embeddable
public class PurchaseOrderLineEmbeddable {

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

    @Column(nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockItemType type;

    @Column(nullable = false)
    private int quantity;

    protected PurchaseOrderLineEmbeddable() {
    }

    public PurchaseOrderLineEmbeddable(
            UUID stockItemId,
            String sku,
            String name,
            StockItemType type,
            int quantity) {
        this.stockItemId = stockItemId;
        this.sku = sku;
        this.name = name;
        this.type = type;
        this.quantity = quantity;
    }

    public UUID getStockItemId() {
        return stockItemId;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public StockItemType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }
}
