package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.event.EstimateGeneratedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EstimateGeneratedNotificationListenerTest {

    private final CustomerEstimateNotificationPort notificationPort = mock(CustomerEstimateNotificationPort.class);
    private final EstimateGeneratedNotificationListener listener =
            new EstimateGeneratedNotificationListener(notificationPort);

    @Test
    void invokesThePortWithTheEventFieldsWhenAnEstimateGeneratedEventIsReceived() {
        UUID estimateId = UUID.randomUUID();
        UUID serviceOrderId = UUID.randomUUID();
        UUID diagnosisId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        EstimateGeneratedEvent event = new EstimateGeneratedEvent(
                UUID.randomUUID(), Instant.now(), estimateId, serviceOrderId, diagnosisId, customerId, expiresAt);

        listener.on(event);

        verify(notificationPort).notifyEstimateGenerated(estimateId, serviceOrderId, customerId, expiresAt);
    }

    @Test
    void doesNotPropagateAnExceptionThrownByThePort() {
        UUID customerId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        EstimateGeneratedEvent event = new EstimateGeneratedEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                customerId, expiresAt);
        doThrow(new RuntimeException("delivery failed"))
                .when(notificationPort)
                .notifyEstimateGenerated(event.estimateId(), event.serviceOrderId(), event.customerId(), expiresAt);

        assertDoesNotThrow(() -> listener.on(event));
    }
}
