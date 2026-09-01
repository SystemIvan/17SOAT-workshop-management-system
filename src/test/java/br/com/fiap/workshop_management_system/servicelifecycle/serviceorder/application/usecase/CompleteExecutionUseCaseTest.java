package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompleteExecutionUseCaseTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-28T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-28T11:30:00.123456Z");
    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void completesAnInProgressExecutionAndMovesServiceOrderToCompleted() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        CompleteExecutionUseCase useCase = new CompleteExecutionUseCase(
                serviceOrders, Clock.fixed(COMPLETED_AT, ZoneOffset.UTC));

        ServiceOrder serviceOrder = newInProgressServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);

        ServiceOrderResponse response = useCase.execute(serviceOrder.id(), executionId);

        assertEquals(ServiceExecutionStatus.COMPLETED, response.executions().get(0).status());
        assertEquals(ServiceOrderStatus.COMPLETED, response.status());
        assertEquals(COMPLETED_AT, serviceOrder.serviceExecutions().getFirst().completedAt());
        assertTrue(serviceOrders.findByIdForUpdateCalled);
    }

    @Test
    void rejectsCompletingAnExecutionThatIsNotInProgress() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        CompleteExecutionUseCase useCase = new CompleteExecutionUseCase(serviceOrders);

        ServiceOrder serviceOrder = newPendingServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);

        assertThrows(IllegalStateException.class, () -> useCase.execute(serviceOrder.id(), executionId));
    }

    @Test
    void rejectsCompletingWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        CompleteExecutionUseCase useCase = new CompleteExecutionUseCase(serviceOrders);

        assertThrows(NoSuchElementException.class,
                () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID()));
    }

    private ServiceOrder newInProgressServiceOrder() {
        ServiceOrder serviceOrder = newPendingServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, STARTED_AT);
        return serviceOrder;
    }

    private ServiceOrder newPendingServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        return serviceOrder;
    }

    private static final class InMemoryServiceOrderRepository implements ServiceOrderRepository {
        private final Map<UUID, ServiceOrder> byId = new HashMap<>();
        private boolean findByIdForUpdateCalled;

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<ServiceOrder> findByIdForUpdate(UUID id) {
            findByIdForUpdateCalled = true;
            return findById(id);
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            byId.put(serviceOrder.id(), serviceOrder);
        }
    }
}
