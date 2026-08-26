package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.time.Instant;
import java.util.UUID;

public record LowStockPurchaseDemandResolutionCommand(UUID occurrenceId, UUID stockItemId, Instant resolvedAt) {

    public LowStockPurchaseDemandResolutionCommand {
        if (occurrenceId == null || stockItemId == null || resolvedAt == null) {
            throw new IllegalArgumentException("Low stock purchase demand resolution must be complete");
        }
    }
}
