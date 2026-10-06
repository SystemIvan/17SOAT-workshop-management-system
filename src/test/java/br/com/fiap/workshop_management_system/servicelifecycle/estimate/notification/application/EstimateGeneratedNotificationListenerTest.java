package br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.event.EstimateGenerated;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port.CustomerEstimateNotificationPort;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
        EstimateGenerated event = new EstimateGenerated(
                UUID.randomUUID(), Instant.now(), estimateId, serviceOrderId, diagnosisId, customerId, expiresAt);

        listener.on(event);

        verify(notificationPort).notifyEstimateGenerated(estimateId, serviceOrderId, customerId, expiresAt);
    }

    @Test
    void doesNotPropagateAnExceptionThrownByThePort() {
        UUID customerId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(24, ChronoUnit.HOURS);
        EstimateGenerated event = new EstimateGenerated(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                customerId, expiresAt);
        doThrow(new RuntimeException("delivery failed"))
                .when(notificationPort)
                .notifyEstimateGenerated(event.estimateId(), event.serviceOrderId(), event.customerId(), expiresAt);

        assertDoesNotThrow(() -> listener.on(event));
    }

    @Test
    void logsDeliveryFailuresWithOpaqueIdsOnly() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger(EstimateGeneratedNotificationListener.class);
        logger.addAppender(appender);
        try {
            EstimateGenerated event = new EstimateGenerated(
                    UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), null);
            doThrow(new IllegalStateException("550 rejected recipient jane.doe@example.com"))
                    .when(notificationPort).notifyEstimateGenerated(any(), any(), any(), any());

            listener.on(event);

            ILoggingEvent logged = appender.list.get(0);
            assertEquals(Level.WARN, logged.getLevel());
            assertTrue(logged.getFormattedMessage().contains(event.estimateId().toString()));
            assertTrue(logged.getFormattedMessage().contains("IllegalStateException"));
            assertFalse(logged.getFormattedMessage().contains("jane.doe@example.com"));
            assertNull(logged.getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
        }
    }
}
