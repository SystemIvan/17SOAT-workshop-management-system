package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssueReason;

import java.util.UUID;

public record StockReservationAttemptIssueResponse(
        UUID stockItemId,
        StockReservationIssueReason reason,
        int requestedQuantity,
        Integer availableQuantity) {
}
