package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ChangeServiceOrderPriorityRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeServiceOrderPriorityUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void changesThePriorityAndPersistsIt() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        ChangeServiceOrderPriorityUseCase useCase = new ChangeServiceOrderPriorityUseCase(serviceOrders);
        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        serviceOrders.save(serviceOrder);

        ServiceOrderResponse response = useCase.execute(
                serviceOrder.id(), new ChangeServiceOrderPriorityRequest(Priority.URGENT));

        assertEquals(Priority.URGENT, response.priority());
        assertEquals(Priority.URGENT, serviceOrders.findById(serviceOrder.id()).orElseThrow().priority());
    }

    @Test
    void rejectsChangingPriorityWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        ChangeServiceOrderPriorityUseCase useCase = new ChangeServiceOrderPriorityUseCase(serviceOrders);

        assertThrows(NoSuchElementException.class,
                () -> useCase.execute(UUID.randomUUID(), new ChangeServiceOrderPriorityRequest(Priority.HIGH)));
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
