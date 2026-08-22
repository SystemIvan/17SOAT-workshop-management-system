package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api;

import java.util.UUID;

public record StockReservationIssue(
        UUID stockItemId,
        StockReservationIssueReason reason,
        int requestedQuantity,
        Integer availableQuantity
) {
}
