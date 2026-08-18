package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
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

class StartExecutionUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void startsAReadyExecutionAndMovesItToInProgress() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        StartExecutionUseCase useCase = new StartExecutionUseCase(serviceOrders);

        ServiceOrder serviceOrder = newReadyServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);

        ServiceOrderResponse response = useCase.execute(serviceOrder.id(), executionId);

        assertEquals(ServiceExecutionStatus.IN_PROGRESS, response.executions().get(0).status());
    }

    @Test
    void rejectsStartingAnExecutionThatIsNotReady() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        StartExecutionUseCase useCase = new StartExecutionUseCase(serviceOrders);

        ServiceOrder serviceOrder = newPendingServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);

        assertThrows(IllegalStateException.class, () -> useCase.execute(serviceOrder.id(), executionId));
    }

    @Test
    void rejectsStartingWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        StartExecutionUseCase useCase = new StartExecutionUseCase(serviceOrders);

        assertThrows(NoSuchElementException.class, () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID()));
    }

    private ServiceOrder newReadyServiceOrder() {
        ServiceOrder serviceOrder = newPendingServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        return serviceOrder;
    }

    private ServiceOrder newPendingServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item));
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