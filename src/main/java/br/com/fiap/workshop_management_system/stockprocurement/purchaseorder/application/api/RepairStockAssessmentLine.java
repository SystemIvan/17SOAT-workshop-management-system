package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.util.Objects;
import java.util.UUID;

public record RepairStockAssessmentLine(UUID stockItemId, int requestedQuantity) {

    public RepairStockAssessmentLine {
        Objects.requireNonNull(stockItemId, "stockItemId must not be null");
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("requestedQuantity must be greater than zero");
        }
    }
}
