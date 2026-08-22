package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.notification;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssue;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssueReason;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationCreatedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationNotReservedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.port.StockManagerNotificationPort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class StockReservationNotificationListenerTest {

    private final StockManagerNotificationPort notificationPort = mock(StockManagerNotificationPort.class);
    private final StockReservationNotificationListener listener =
            new StockReservationNotificationListener(notificationPort);

    @Test
    void notifiesTheStockManagerForCreatedAndUnavailableReservations() {
        UUID reservationId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        List<ReserveStockItem> items = List.of(new ReserveStockItem(UUID.randomUUID(), 2));
        List<StockReservationIssue> issues = List.of(new StockReservationIssue(
                UUID.randomUUID(), StockReservationIssueReason.INSUFFICIENT_QUANTITY, 2, 1));

        listener.on(new StockReservationCreatedEvent(reservationId, executionId, items));
        listener.on(new StockReservationNotReservedEvent(executionId, issues));

        verify(notificationPort).notifyStockReservationCreated(reservationId, executionId, items);
        verify(notificationPort).notifyStockReservationUnavailable(executionId, issues);
    }

    @Test
    void doesNotPropagateDeliveryFailures() {
        UUID reservationId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        List<ReserveStockItem> items = List.of(new ReserveStockItem(UUID.randomUUID(), 2));
        doThrow(new RuntimeException("delivery failed"))
                .when(notificationPort)
                .notifyStockReservationCreated(reservationId, executionId, items);

        assertDoesNotThrow(() -> listener.on(new StockReservationCreatedEvent(reservationId, executionId, items)));
    }
}
