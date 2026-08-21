package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.notification;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.event.TechnicianMaterialsReservedEvent;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.TechnicianNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class TechnicianMaterialsReservedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(TechnicianMaterialsReservedNotificationListener.class);

    private final TechnicianNotificationPort notificationPort;

    TechnicianMaterialsReservedNotificationListener(TechnicianNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(TechnicianMaterialsReservedEvent event) {
        try {
            notificationPort.notifyMaterialsReserved(
                    event.serviceOrderId(),
                    event.serviceExecutionId(),
                    event.technicianId(),
                    event.stockReservationId());
        } catch (RuntimeException exception) {
            log.warn("Unable to notify technician {} about reservation {} for execution {}",
                    event.technicianId(), event.stockReservationId(), event.serviceExecutionId(), exception);
        }
    }
}
