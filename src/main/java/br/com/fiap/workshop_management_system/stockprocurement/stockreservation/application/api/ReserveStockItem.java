package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api;

import java.util.UUID;

public record ReserveStockItem(UUID stockItemId, int quantity) {

    public ReserveStockItem {
        if (stockItemId == null || quantity <= 0) {
            throw new IllegalArgumentException("Stock item id and a positive quantity are required");
        }
    }
}
