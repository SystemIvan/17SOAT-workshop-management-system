package br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model;

import java.util.UUID;

/**
 * Value Object. "reserved" changes over time - the whole instance is replaced
 * via {@link #reserved()}, never mutated in place.
 */
public record StockRequirement(
        UUID stockItemId,
        StockItemType type,
        int quantity,
        String nameSnapshot,
        Money priceSnapshot,
        boolean reserved
) {

    public StockRequirement {
        if (quantity <= 0) {
            throw new IllegalArgumentException("StockRequirement quantity must be > 0");
        }
    }

    public boolean isSameItem(UUID otherStockItemId) {
        return stockItemId.equals(otherStockItemId);
    }

    public StockRequirement withReserved() {
        return new StockRequirement(stockItemId, type, quantity, nameSnapshot, priceSnapshot, true);
    }
}