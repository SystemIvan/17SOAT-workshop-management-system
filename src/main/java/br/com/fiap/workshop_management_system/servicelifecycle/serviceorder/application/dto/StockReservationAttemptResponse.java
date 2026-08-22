package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReservationAttemptOutcome;

import java.util.List;
import java.util.UUID;

public record StockReservationAttemptResponse(
        UUID serviceExecutionId,
        ReservationAttemptOutcome outcome,
        UUID reservationId,
        List<StockReservationAttemptIssueResponse> issues) {

    public StockReservationAttemptResponse {
        issues = List.copyOf(issues);
    }
}
