package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RepairStockAssessmentResultLine(
        UUID stockItemId,
        int requestedQuantity,
        int observedAvailableQuantity,
        int shortageQuantity,
        RepairStockAvailabilityStatus status,
        Instant observedAt) {

    public RepairStockAssessmentResultLine {
        Objects.requireNonNull(stockItemId, "stockItemId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (requestedQuantity <= 0 || observedAvailableQuantity < 0 || shortageQuantity < 0
                || shortageQuantity != Math.max(requestedQuantity - observedAvailableQuantity, 0)
                || (status == RepairStockAvailabilityStatus.AVAILABLE) != (shortageQuantity == 0)) {
            throw new IllegalArgumentException("Repair stock assessment quantities are inconsistent");
        }
    }
}
