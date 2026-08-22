package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.notification;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssue;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationCreatedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.port.StockManagerNotificationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(StockReservationNotificationAfterCommitTest.NotificationTestConfiguration.class)
class StockReservationNotificationAfterCommitTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private RecordingStockManagerNotificationPort notificationPort;

    private TransactionTemplate transactionTemplate;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void deliversOnlyAfterCommitAndSkipsRolledBackTransactions() {
        StockReservationCreatedEvent committedEvent = event();

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(committedEvent);
            assertTrue(notificationPort.createdCalls().isEmpty());
        });

        assertEquals(List.of(new RecordingStockManagerNotificationPort.CreatedCall(
                committedEvent.reservationId(), committedEvent.serviceExecutionId(), committedEvent.items())),
                notificationPort.createdCalls());

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event());
            status.setRollbackOnly();
        });

        assertEquals(1, notificationPort.createdCalls().size());
    }

    private StockReservationCreatedEvent event() {
        return new StockReservationCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), List.of(new ReserveStockItem(UUID.randomUUID(), 2)));
    }

    @TestConfiguration
    static class NotificationTestConfiguration {

        @Bean
        @Primary
        RecordingStockManagerNotificationPort recordingStockManagerNotificationPort() {
            return new RecordingStockManagerNotificationPort();
        }
    }

    static class RecordingStockManagerNotificationPort implements StockManagerNotificationPort {

        private final List<CreatedCall> createdCalls = new CopyOnWriteArrayList<>();

        @Override
        public void notifyStockReservationCreated(
                UUID reservationId,
                UUID serviceExecutionId,
                List<ReserveStockItem> items) {
            createdCalls.add(new CreatedCall(reservationId, serviceExecutionId, items));
        }

        @Override
        public void notifyStockReservationUnavailable(UUID serviceExecutionId, List<StockReservationIssue> issues) {
        }

        List<CreatedCall> createdCalls() {
            return List.copyOf(createdCalls);
        }

        record CreatedCall(UUID reservationId, UUID serviceExecutionId, List<ReserveStockItem> items) {
        }
    }
}
