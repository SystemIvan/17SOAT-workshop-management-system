package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.port;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssue;

import java.util.List;
import java.util.UUID;

public interface StockManagerNotificationPort {

    void notifyStockReservationCreated(UUID reservationId, UUID serviceExecutionId, List<ReserveStockItem> items);

    void notifyStockReservationUnavailable(UUID serviceExecutionId, List<StockReservationIssue> issues);
}
