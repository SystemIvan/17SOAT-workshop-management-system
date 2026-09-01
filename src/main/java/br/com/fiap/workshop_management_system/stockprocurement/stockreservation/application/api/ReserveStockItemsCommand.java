package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api;

import java.util.List;
import java.util.UUID;

public record ReserveStockItemsCommand(UUID serviceExecutionId, List<ReserveStockItem> items) {

    public ReserveStockItemsCommand {
        if (serviceExecutionId == null || items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Service execution id and at least one stock item are required");
        }
        items = List.copyOf(items);
    }
}
