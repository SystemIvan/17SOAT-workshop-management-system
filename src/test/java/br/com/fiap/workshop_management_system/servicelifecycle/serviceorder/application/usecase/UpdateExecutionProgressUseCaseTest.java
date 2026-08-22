package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.UpdateExecutionProgressRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateExecutionProgressUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void updatesProgressOfAnInProgressExecution() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        UpdateExecutionProgressUseCase useCase = new UpdateExecutionProgressUseCase(serviceOrders);

        ServiceOrder serviceOrder = newInProgressServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);

        ServiceOrderResponse response = useCase.execute(
                serviceOrder.id(), executionId, new UpdateExecutionProgressRequest("Peça trocada"));

        assertEquals(ServiceExecutionStatus.IN_PROGRESS, response.executions().get(0).status());
    }

    @Test
    void rejectsUpdatingProgressOfAnExecutionThatIsNotInProgress() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        UpdateExecutionProgressUseCase useCase = new UpdateExecutionProgressUseCase(serviceOrders);

        ServiceOrder serviceOrder = newPendingServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(serviceOrder.id(), executionId, new UpdateExecutionProgressRequest("nota")));
    }

    @Test
    void rejectsUpdatingProgressWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        UpdateExecutionProgressUseCase useCase = new UpdateExecutionProgressUseCase(serviceOrders);

        assertThrows(NoSuchElementException.class, () -> useCase.execute(
                UUID.randomUUID(), UUID.randomUUID(), new UpdateExecutionProgressRequest("nota")));
    }

    private ServiceOrder newInProgressServiceOrder() {
        ServiceOrder serviceOrder = newPendingServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.startExecution(executionId);
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

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            byId.put(serviceOrder.id(), serviceOrder);
        }
    }
}
