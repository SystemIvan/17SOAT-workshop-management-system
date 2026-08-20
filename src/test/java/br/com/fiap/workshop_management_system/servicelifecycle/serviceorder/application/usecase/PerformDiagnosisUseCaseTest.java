package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.DiagnosisItemRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.MoneyDTO;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
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

class PerformDiagnosisUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void recordsDiagnosisItemsAsServiceExecutions() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders);

        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        serviceOrders.save(serviceOrder);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(List.of(
                new DiagnosisItemRequest(UUID.randomUUID(), "Troca de óleo",
                        new MoneyDTO(BigDecimal.TEN, "BRL"), null),
                new DiagnosisItemRequest(UUID.randomUUID(), "Alinhamento",
                        new MoneyDTO(BigDecimal.ONE, "BRL"), null)));

        ServiceOrderResponse response = useCase.execute(serviceOrder.id(), request);

        assertEquals(2, response.executions().size());
    }

    @Test
    void rejectsDiagnosisWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(List.of(
                new DiagnosisItemRequest(UUID.randomUUID(), "Troca de óleo",
                        new MoneyDTO(BigDecimal.TEN, "BRL"), null)));

        assertThrows(NoSuchElementException.class, () -> useCase.execute(UUID.randomUUID(), request));
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
