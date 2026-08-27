package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model;

import java.util.UUID;

public record StockReceiptLine(
        UUID movementId,
        UUID stockItemId,
        int quantity,
        int availableBefore,
        int availableAfter) {

    public StockReceiptLine {
        if (movementId == null || stockItemId == null || quantity <= 0 || availableBefore < 0
                || availableAfter < 0 || availableAfter - availableBefore != quantity) {
            throw new IllegalArgumentException("Stock receipt line is inconsistent");
        }
    }
}
