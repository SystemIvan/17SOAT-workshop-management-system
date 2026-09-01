package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.event.EstimateGenerated;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reacts to the real {@link EstimateGenerated} domain event, published by
 * {@code GenerateEstimateUseCase} via {@code ApplicationEventPublisher} (reconciled after
 * feat/servicelifecycle-estimate-generation merged; see technical-spec.md, "Contexto e desenho").
 * {@link ApplicationModuleListener} runs asynchronously after the publishing transaction commits,
 * which is why a delivery failure here can never affect the Estimate generation that triggered it.
 */
@Component
class EstimateGeneratedNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(EstimateGeneratedNotificationListener.class);

    private final CustomerEstimateNotificationPort notificationPort;

    EstimateGeneratedNotificationListener(CustomerEstimateNotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    @ApplicationModuleListener
    void on(EstimateGenerated event) {
        try {
            notificationPort.notifyEstimateGenerated(
                    event.estimateId(), event.serviceOrderId(), event.customerId(), event.expiresAt());
        } catch (RuntimeException ex) {
            log.warn("Failed to notify customer {} about generated estimate {}",
                    event.customerId(), event.estimateId(), ex);
        }
    }
}
