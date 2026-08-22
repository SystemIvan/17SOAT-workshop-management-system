package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssue;

import java.util.List;
import java.util.UUID;

public record StockReservationNotReservedEvent(
        UUID serviceExecutionId,
        List<StockReservationIssue> issues) {

    public StockReservationNotReservedEvent {
        issues = List.copyOf(issues);
    }
}
