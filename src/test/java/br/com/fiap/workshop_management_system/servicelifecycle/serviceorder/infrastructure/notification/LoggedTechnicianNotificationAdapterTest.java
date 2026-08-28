package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggedTechnicianNotificationAdapterTest {

    private final LoggedTechnicianNotificationAdapter adapter = new LoggedTechnicianNotificationAdapter();

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
        return (Logger) LoggerFactory.getLogger(LoggedTechnicianNotificationAdapter.class);
    }

    @Test
    void logsAnInfoLineWithServiceOrderAndTechnicianIds() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();

        adapter.notifyServiceOrderCreated(serviceOrderId, technicianId);

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains(serviceOrderId.toString()));
        assertTrue(message.contains(technicianId.toString()));
    }

    @Test
    void logsAnInfoLineWithMaterialsReservedDetails() {
        UUID serviceOrderId = UUID.randomUUID();
        UUID serviceExecutionId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID stockReservationId = UUID.randomUUID();

        adapter.notifyMaterialsReserved(serviceOrderId, serviceExecutionId, technicianId, stockReservationId);

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        String message = event.getFormattedMessage();
        assertTrue(message.contains(serviceOrderId.toString()));
        assertTrue(message.contains(serviceExecutionId.toString()));
        assertTrue(message.contains(technicianId.toString()));
        assertTrue(message.contains(stockReservationId.toString()));
    }
}
