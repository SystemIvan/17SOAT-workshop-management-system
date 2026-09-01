package br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.event;

import java.time.Instant;
import java.util.UUID;

public record LowStockDetectedEvent(
        UUID occurrenceId,
        UUID stockItemId,
        int observedAvailableQuantity,
        int minimumQuantity,
        int targetQuantity,
        int suggestedQuantity,
        Instant detectedAt) {

    public LowStockDetectedEvent {
        if (occurrenceId == null || stockItemId == null || detectedAt == null || observedAvailableQuantity < 0
                || minimumQuantity < 0 || targetQuantity <= minimumQuantity || suggestedQuantity <= 0) {
            throw new IllegalArgumentException("Low stock detected event data is invalid");
        }
    }
}
