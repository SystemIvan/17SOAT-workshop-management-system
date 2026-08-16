package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;

import java.util.Objects;
import java.util.UUID;

public record EstimateStockItem(
        UUID stockItemId,
        StockItemType type,
        int quantity,
        String nameSnapshot,
        Money priceSnapshot
) {
    public EstimateStockItem {
        Objects.requireNonNull(stockItemId, "stockItemId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(nameSnapshot, "nameSnapshot must not be null");
        Objects.requireNonNull(priceSnapshot, "priceSnapshot must not be null");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        if (nameSnapshot.isBlank()) {
            throw new IllegalArgumentException("nameSnapshot must not be blank");
        }
    }
}
