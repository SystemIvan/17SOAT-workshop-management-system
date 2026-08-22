package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

@Embeddable
public class StockReservationLineEmbeddable {

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

    @Column(nullable = false)
    private int quantity;

    protected StockReservationLineEmbeddable() {
    }

    public StockReservationLineEmbeddable(UUID stockItemId, int quantity) {
        this.stockItemId = stockItemId;
        this.quantity = quantity;
    }

    public UUID getStockItemId() {
        return stockItemId;
    }

    public int getQuantity() {
        return quantity;
    }
}
