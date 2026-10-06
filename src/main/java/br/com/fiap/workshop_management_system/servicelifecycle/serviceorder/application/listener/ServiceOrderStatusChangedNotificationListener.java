package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.listener;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.event.ServiceOrderStatusChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * RF52 - notifies the Customer when the nominal (RF39) status of a Service Order changes. Internal transitions that
 * keep the nominal status (e.g. AWAITING_ITEMS -> IN_PROGRESS, both "Execução") are ignored.
 *
 * <p>{@link ApplicationModuleListener} runs asynchronously after the publishing transaction commits, so a delivery
 * failure can never roll back the status change. Failures are still caught here so they are logged with opaque IDs
 * only, instead of reaching the generic async handler with a message that may contain the recipient address.
 */
@Component
class ServiceOrderStatusChangedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ServiceOrderStatusChangedNotificationListener.class);

    private final CustomerNotificationPort notificationPort;

    ServiceOrderStatusChangedNotificationListener(CustomerNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @ApplicationModuleListener
    void on(ServiceOrderStatusChanged event) {
        ServiceOrderStatusLabel previous = ServiceOrderStatusLabel.from(event.previousStatus());
        ServiceOrderStatusLabel current = ServiceOrderStatusLabel.from(event.currentStatus());
        if (previous == current) {
            return;
        }
        try {
            notificationPort.notifyServiceOrderStatusChanged(event.serviceOrderId(), event.customerId(), current);
        } catch (RuntimeException ex) {
            log.warn("Failed to notify customer {} about service order {} status change to {}: {}",
                    event.customerId(), event.serviceOrderId(), current, ex.getClass().getSimpleName());
        }
    }
}
