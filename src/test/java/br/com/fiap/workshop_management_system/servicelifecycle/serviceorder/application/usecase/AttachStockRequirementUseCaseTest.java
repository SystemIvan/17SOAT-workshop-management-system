package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.MoneyDTO;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.StockRequirementRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
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

class AttachStockRequirementUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    private StockRequirementRequest newRequest() {
        return new StockRequirementRequest(
                UUID.randomUUID(), StockItemType.PART, 2, "Correia dentada", new MoneyDTO(BigDecimal.TEN, "BRL"));
    }

    private ServiceOrder serviceOrderWithOneExecution(InMemoryServiceOrderRepository serviceOrders) {
        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item));
        serviceOrders.save(serviceOrder);
        return serviceOrder;
    }

    @Test
    void attachesTheStockRequirementAndPersistsIt() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        AttachStockRequirementUseCase useCase = new AttachStockRequirementUseCase(serviceOrders);
        ServiceOrder serviceOrder = serviceOrderWithOneExecution(serviceOrders);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();

        ServiceOrderResponse response = useCase.execute(serviceOrder.id(), executionId, newRequest());

        assertEquals(1, response.executions().get(0).stockRequirements().size());
        assertEquals(false, response.executions().get(0).stockRequirements().get(0).reserved());
        assertEquals(1, serviceOrders.findById(serviceOrder.id()).orElseThrow()
                .serviceExecutions().get(0).stockRequirements().size());
    }

    @Test
    void rejectsAttachingWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        AttachStockRequirementUseCase useCase = new AttachStockRequirementUseCase(serviceOrders);

        assertThrows(NoSuchElementException.class,
                () -> useCase.execute(UUID.randomUUID(), UUID.randomUUID(), newRequest()));
    }

    @Test
    void rejectsAttachingWhenServiceExecutionDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        AttachStockRequirementUseCase useCase = new AttachStockRequirementUseCase(serviceOrders);
        ServiceOrder serviceOrder = serviceOrderWithOneExecution(serviceOrders);

        assertThrows(NoSuchElementException.class,
                () -> useCase.execute(serviceOrder.id(), UUID.randomUUID(), newRequest()));
    }

    @Test
    void rejectsAttachingWhenServiceExecutionIsCompleted() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        AttachStockRequirementUseCase useCase = new AttachStockRequirementUseCase(serviceOrders);
        ServiceOrder serviceOrder = serviceOrderWithOneExecution(serviceOrders);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.startExecution(executionId);
        serviceOrder.completeExecution(executionId);
        serviceOrders.save(serviceOrder);

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(serviceOrder.id(), executionId, newRequest()));
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
