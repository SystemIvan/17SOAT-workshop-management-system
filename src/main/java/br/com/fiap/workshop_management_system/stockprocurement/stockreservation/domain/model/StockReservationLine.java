package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model;

import java.util.UUID;

public record StockReservationLine(UUID stockItemId, int quantity) {

    public StockReservationLine {
        if (stockItemId == null) {
            throw new IllegalArgumentException("Stock item id must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reserved quantity must be greater than zero");
        }
    }
}
