package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class StockReceiptLineEmbeddable {

    @Column(name = "movement_id", nullable = false)
    private UUID movementId;

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "available_before", nullable = false)
    private int availableBefore;

    @Column(name = "available_after", nullable = false)
    private int availableAfter;

    protected StockReceiptLineEmbeddable() {
    }

    public StockReceiptLineEmbeddable(
            UUID movementId, UUID stockItemId, int quantity, int availableBefore, int availableAfter) {
        this.movementId = movementId;
        this.stockItemId = stockItemId;
        this.quantity = quantity;
        this.availableBefore = availableBefore;
        this.availableAfter = availableAfter;
    }

    public UUID getMovementId() {
        return movementId;
    }

    public UUID getStockItemId() {
        return stockItemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getAvailableBefore() {
        return availableBefore;
    }

    public int getAvailableAfter() {
        return availableAfter;
    }
}
