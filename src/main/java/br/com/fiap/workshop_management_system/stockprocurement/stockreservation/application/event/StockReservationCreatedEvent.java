package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;

import java.util.List;
import java.util.UUID;

public record StockReservationCreatedEvent(
        UUID reservationId,
        UUID serviceExecutionId,
        List<ReserveStockItem> items) {

    public StockReservationCreatedEvent {
        items = List.copyOf(items);
    }
}
