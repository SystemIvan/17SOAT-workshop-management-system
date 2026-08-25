package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StockAvailabilitySnapshot(
        UUID stockItemId,
        int requestedQuantity,
        int observedAvailableQuantity,
        int shortageQuantity,
        StockAvailabilityStatus status,
        Instant observedAt) {

    public StockAvailabilitySnapshot {
        Objects.requireNonNull(stockItemId, "stockItemId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (requestedQuantity <= 0 || observedAvailableQuantity < 0 || shortageQuantity < 0
                || shortageQuantity != Math.max(requestedQuantity - observedAvailableQuantity, 0)
                || (status == StockAvailabilityStatus.AVAILABLE) != (shortageQuantity == 0)) {
            throw new IllegalArgumentException("Stock availability snapshot is inconsistent");
        }
    }
}
