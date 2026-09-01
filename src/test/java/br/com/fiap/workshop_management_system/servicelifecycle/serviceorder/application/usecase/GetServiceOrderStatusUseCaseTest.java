package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
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

class GetServiceOrderStatusUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void returnsIdAndStatusForANewlyCreatedServiceOrder() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        GetServiceOrderStatusUseCase useCase = new GetServiceOrderStatusUseCase(serviceOrders);

        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        serviceOrders.save(serviceOrder);

        ServiceOrderStatusResponse response = useCase.execute(serviceOrder.id());

        assertEquals(serviceOrder.id(), response.id());
        assertEquals(ServiceOrderStatus.RECEIVED, response.status());
    }

    @Test
    void returnsIdAndStatusForAServiceOrderInDiagnosis() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        GetServiceOrderStatusUseCase useCase = new GetServiceOrderStatusUseCase(serviceOrders);

        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        serviceOrders.save(serviceOrder);

        ServiceOrderStatusResponse response = useCase.execute(serviceOrder.id());

        assertEquals(serviceOrder.id(), response.id());
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, response.status());
    }

    @Test
    void rejectsGettingStatusWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        GetServiceOrderStatusUseCase useCase = new GetServiceOrderStatusUseCase(serviceOrders);

        assertThrows(NoSuchElementException.class, () -> useCase.execute(UUID.randomUUID()));
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
