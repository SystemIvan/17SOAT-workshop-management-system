package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.listener;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.event.ServiceOrderStatusChanged;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class ServiceOrderStatusChangedNotificationListenerTest {

    private final CustomerNotificationPort notificationPort = mock(CustomerNotificationPort.class);
    private final ServiceOrderStatusChangedNotificationListener listener =
            new ServiceOrderStatusChangedNotificationListener(notificationPort);

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger().detachAppender(appender);
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(ServiceOrderStatusChangedNotificationListener.class);
    }

    @ParameterizedTest
    @CsvSource({
            "RECEIVED, IN_DIAGNOSIS, DIAGNOSTICO",
            "IN_DIAGNOSIS, AWAITING_APPROVAL, AGUARDANDO_APROVACAO",
            "AWAITING_APPROVAL, IN_PROGRESS, EXECUCAO",
            "AWAITING_APPROVAL, AWAITING_ITEMS, EXECUCAO",
            "IN_PROGRESS, COMPLETED, FINALIZADA",
            "AWAITING_APPROVAL, COMPLETED, FINALIZADA",
            "COMPLETED, DELIVERED, ENTREGUE"
    })
    void notifiesTheNewNominalStatusOnceWhenTheNominalStatusChanges(
            ServiceOrderStatus previous, ServiceOrderStatus current, ServiceOrderStatusLabel expected) {
        ServiceOrderStatusChanged event = event(previous, current);

        listener.on(event);

        verify(notificationPort).notifyServiceOrderStatusChanged(event.serviceOrderId(), event.customerId(), expected);
    }

    @ParameterizedTest
    @CsvSource({
            "AWAITING_ITEMS, IN_PROGRESS",
            "IN_PROGRESS, AWAITING_ITEMS"
    })
    void ignoresInternalTransitionsThatKeepTheNominalStatus(ServiceOrderStatus previous, ServiceOrderStatus current) {
        listener.on(event(previous, current));

        verifyNoInteractions(notificationPort);
    }

    @Test
    void swallowsDeliveryFailuresAndLogsOnlyOpaqueIds() {
        ServiceOrderStatusChanged event = event(ServiceOrderStatus.COMPLETED, ServiceOrderStatus.DELIVERED);
        doThrow(new IllegalStateException("550 rejected recipient jane.doe@example.com"))
                .when(notificationPort).notifyServiceOrderStatusChanged(any(), any(), any());

        listener.on(event);

        assertEquals(1, appender.list.size());
        ILoggingEvent logged = appender.list.get(0);
        assertEquals(Level.WARN, logged.getLevel());
        String message = logged.getFormattedMessage();
        assertTrue(message.contains(event.serviceOrderId().toString()));
        assertTrue(message.contains(event.customerId().toString()));
        assertTrue(message.contains("IllegalStateException"));
        assertFalse(message.contains("jane.doe@example.com"), "log must not echo the exception message");
        assertNull(logged.getThrowableProxy(), "log must not carry the exception, whose message may hold PII");
    }

    private ServiceOrderStatusChanged event(ServiceOrderStatus previous, ServiceOrderStatus current) {
        return new ServiceOrderStatusChanged(UUID.randomUUID(), UUID.randomUUID(), previous, current, Instant.now());
    }
}
