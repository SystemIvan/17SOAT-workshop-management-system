package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;

public final class StockReservationAttemptMapper {

    private StockReservationAttemptMapper() {
    }

    public static StockReservationAttemptResponse toResponse(ReserveStockItemsResult result) {
        return new StockReservationAttemptResponse(
                result.serviceExecutionId(),
                result.outcome(),
                result.reservationId(),
                result.issues().stream()
                        .map(issue -> new StockReservationAttemptIssueResponse(
                                issue.stockItemId(),
                                issue.reason(),
                                issue.requestedQuantity(),
                                issue.availableQuantity()))
                        .toList());
    }
}
