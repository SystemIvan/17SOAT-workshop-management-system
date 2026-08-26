package br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.notification;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.event.LowStockDetectedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.port.LowStockNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class LowStockNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockNotificationListener.class);
    private final LowStockNotificationPort notificationPort;

    LowStockNotificationListener(LowStockNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(LowStockDetectedEvent event) {
        try {
            notificationPort.notifyLowStockDetected(event);
        } catch (RuntimeException exception) {
            log.warn("Unable to notify stock manager about low stock occurrence {} for stock item {}",
                    event.occurrenceId(), event.stockItemId(), exception);
        }
    }
}
