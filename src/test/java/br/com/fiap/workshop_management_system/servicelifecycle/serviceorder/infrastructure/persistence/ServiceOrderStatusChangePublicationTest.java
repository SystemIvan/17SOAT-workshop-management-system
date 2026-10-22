package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.event.ServiceOrderStatusChanged;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF52 - {@link ServiceOrderRepository#save} publishes exactly one {@link ServiceOrderStatusChanged} per persisted
 * transition, and none for creation or for saves that keep the status.
 */
@SpringBootTest
@RecordApplicationEvents
class ServiceOrderStatusChangePublicationTest {

    @Autowired
    private ServiceOrderRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void savingANewServiceOrderPublishesNothing() {
        ServiceOrder serviceOrder = newServiceOrder();

        inTransaction(() -> repository.save(serviceOrder));

        assertTrue(eventsFor(serviceOrder.id()).isEmpty());
    }

    @Test
    void savingATransitionPublishesOneEventAndSavingAgainPublishesNoMore() {
        ServiceOrder serviceOrder = newServiceOrder();
        inTransaction(() -> repository.save(serviceOrder));

        serviceOrder.performDiagnosis(List.of(diagnosisItem()), UUID.randomUUID(), Instant.EPOCH);
        inTransaction(() -> {
            repository.save(serviceOrder);
            repository.save(serviceOrder);
        });

        List<ServiceOrderStatusChanged> events = eventsFor(serviceOrder.id());
        assertEquals(1, events.size());
        assertEquals(serviceOrder.customerId(), events.get(0).customerId());
        assertEquals(ServiceOrderStatus.RECEIVED, events.get(0).previousStatus());
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, events.get(0).currentStatus());
    }

    @Test
    void reloadedServiceOrderPublishesTheTransitionFromItsPersistedStatus() {
        ServiceOrder serviceOrder = newServiceOrder();
        serviceOrder.performDiagnosis(List.of(diagnosisItem()), UUID.randomUUID(), Instant.EPOCH);
        inTransaction(() -> repository.save(serviceOrder));
        applicationEvents.clear();

        inTransaction(() -> {
            ServiceOrder reloaded = repository.findById(serviceOrder.id()).orElseThrow();
            reloaded.markEstimateSentWithPendingLines();
            repository.save(reloaded);
        });

        List<ServiceOrderStatusChanged> events = eventsFor(serviceOrder.id());
        assertEquals(1, events.size());
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, events.get(0).previousStatus());
        assertEquals(ServiceOrderStatus.AWAITING_APPROVAL, events.get(0).currentStatus());
    }

    @Test
    void savingWithoutStatusChangePublishesNothing() {
        ServiceOrder serviceOrder = newServiceOrder();
        inTransaction(() -> repository.save(serviceOrder));

        inTransaction(() -> {
            ServiceOrder reloaded = repository.findById(serviceOrder.id()).orElseThrow();
            reloaded.definePriority(Priority.URGENT);
            repository.save(reloaded);
        });

        assertTrue(eventsFor(serviceOrder.id()).isEmpty());
    }

    private ServiceOrder newServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        return serviceOrder;
    }

    private DiagnosisItem diagnosisItem() {
        return new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private List<ServiceOrderStatusChanged> eventsFor(UUID serviceOrderId) {
        return applicationEvents.stream(ServiceOrderStatusChanged.class)
                .filter(event -> event.serviceOrderId().equals(serviceOrderId))
                .toList();
    }
}
