package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.util.UUID;

public record LowStockPurchaseDemandCommand(
        UUID occurrenceId,
        UUID stockItemId,
        int observedAvailableQuantity,
        int suggestedQuantity) {

    public LowStockPurchaseDemandCommand {
        if (occurrenceId == null || stockItemId == null) {
            throw new IllegalArgumentException("Low stock purchase demand identifiers must not be null");
        }
        if (observedAvailableQuantity < 0 || suggestedQuantity <= 0) {
            throw new IllegalArgumentException("Low stock purchase demand quantities are invalid");
        }
    }
}
