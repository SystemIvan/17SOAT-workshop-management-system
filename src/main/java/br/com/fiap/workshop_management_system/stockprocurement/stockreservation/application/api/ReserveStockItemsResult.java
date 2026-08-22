package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api;

import java.util.List;
import java.util.UUID;

public record ReserveStockItemsResult(
        UUID serviceExecutionId,
        ReservationAttemptOutcome outcome,
        UUID reservationId,
        boolean newlyCreated,
        List<ReserveStockItem> items,
        List<StockReservationIssue> issues
) {

    public ReserveStockItemsResult {
        items = List.copyOf(items);
        issues = List.copyOf(issues);
    }
}
