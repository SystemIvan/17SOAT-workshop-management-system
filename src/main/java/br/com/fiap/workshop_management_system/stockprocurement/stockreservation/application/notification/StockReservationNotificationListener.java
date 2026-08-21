package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.notification;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationCreatedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationNotReservedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.port.StockManagerNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class StockReservationNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(StockReservationNotificationListener.class);

    private final StockManagerNotificationPort notificationPort;

    StockReservationNotificationListener(StockManagerNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(StockReservationCreatedEvent event) {
        try {
            notificationPort.notifyStockReservationCreated(
                    event.reservationId(), event.serviceExecutionId(), event.items());
        } catch (RuntimeException exception) {
            log.warn("Unable to notify stock manager about reservation {} for execution {}",
                    event.reservationId(), event.serviceExecutionId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(StockReservationNotReservedEvent event) {
        try {
            notificationPort.notifyStockReservationUnavailable(event.serviceExecutionId(), event.issues());
        } catch (RuntimeException exception) {
            log.warn("Unable to notify stock manager about unavailable items for execution {}",
                    event.serviceExecutionId(), exception);
        }
    }
}
