package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.event.EstimateGeneratedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to Estimate generation as a true domain event, not a synchronous call from the use case
 * that generates the Estimate - that use case belongs to another developer and is not edited by
 * this feature (see technical-spec.md, "Contexto e desenho"). {@link ApplicationModuleListener}
 * runs asynchronously after the publishing transaction commits, which is why a delivery failure
 * here can never affect the Estimate generation that triggered it.
 */
@Component
class EstimateGeneratedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EstimateGeneratedNotificationListener.class);

    private final CustomerEstimateNotificationPort notificationPort;

    EstimateGeneratedNotificationListener(CustomerEstimateNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @ApplicationModuleListener
    void on(EstimateGeneratedEvent event) {
        try {
            notificationPort.notifyEstimateGenerated(
                    event.estimateId(), event.serviceOrderId(), event.customerId(), event.expiresAt());
        } catch (RuntimeException ex) {
            log.warn("Failed to notify customer {} about generated estimate {}",
                    event.customerId(), event.estimateId(), ex);
        }
    }
}
