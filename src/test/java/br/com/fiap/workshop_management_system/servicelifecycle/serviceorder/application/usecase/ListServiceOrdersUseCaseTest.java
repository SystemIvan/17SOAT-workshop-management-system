package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderSearchCriteria;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListServiceOrdersUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void passesTheReceivedCriteriaThroughToTheRepositoryUnchanged() {
        RecordingServiceOrderRepository serviceOrders = new RecordingServiceOrderRepository();
        ListServiceOrdersUseCase useCase = new ListServiceOrdersUseCase(serviceOrders);
        ServiceOrderSearchCriteria criteria = new ServiceOrderSearchCriteria(
                ServiceOrderStatus.IN_DIAGNOSIS, UUID.randomUUID(), UUID.randomUUID(), Priority.HIGH);

        useCase.execute(criteria);

        assertSame(criteria, serviceOrders.lastCriteria);
    }

    @Test
    void mapsEveryServiceOrderReturnedByTheRepositoryToAResponse() {
        RecordingServiceOrderRepository serviceOrders = new RecordingServiceOrderRepository();
        ListServiceOrdersUseCase useCase = new ListServiceOrdersUseCase(serviceOrders);
        ServiceOrder first = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Ruído ao frear");
        ServiceOrder second = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Troca de óleo");
        serviceOrders.results.add(first);
        serviceOrders.results.add(second);

        List<ServiceOrderResponse> responses = useCase.execute(new ServiceOrderSearchCriteria(null, null, null, null));

        assertEquals(2, responses.size());
        assertEquals(first.id(), responses.get(0).id());
        assertEquals(second.id(), responses.get(1).id());
    }

    @Test
    void returnsAnEmptyListWhenTheRepositoryFindsNothing() {
        RecordingServiceOrderRepository serviceOrders = new RecordingServiceOrderRepository();
        ListServiceOrdersUseCase useCase = new ListServiceOrdersUseCase(serviceOrders);

        List<ServiceOrderResponse> responses = useCase.execute(new ServiceOrderSearchCriteria(null, null, null, null));

        assertTrue(responses.isEmpty());
    }

    private static final class RecordingServiceOrderRepository implements ServiceOrderRepository {
        private final Map<UUID, ServiceOrder> byId = new HashMap<>();
        private final List<ServiceOrder> results = new ArrayList<>();
        private ServiceOrderSearchCriteria lastCriteria;

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<ServiceOrder> search(ServiceOrderSearchCriteria criteria) {
            this.lastCriteria = criteria;
            return results;
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            byId.put(serviceOrder.id(), serviceOrder);
        }
    }
}
