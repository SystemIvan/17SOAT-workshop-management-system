package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockItemsRestockedEvent(
        UUID stockReceiptId,
        UUID purchaseOrderId,
        List<UUID> stockItemIds,
        Instant occurredAt) {

    public StockItemsRestockedEvent {
        if (stockReceiptId == null || purchaseOrderId == null || occurredAt == null || stockItemIds == null
                || stockItemIds.isEmpty()) {
            throw new IllegalArgumentException("Stock restocked event required data must not be null or empty");
        }
        if (stockItemIds.stream().anyMatch(java.util.Objects::isNull) || stockItemIds.stream().distinct().count()
                != stockItemIds.size()) {
            throw new IllegalArgumentException("Stock restocked event item ids must be unique");
        }
        List<UUID> orderedIds = stockItemIds.stream().sorted().toList();
        stockItemIds = List.copyOf(orderedIds);
    }
}
