package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.DiagnosisItemRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.MoneyDTO;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Specialty;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformDiagnosisUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void recordsDiagnosisItemsAsServiceExecutions() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        Instant diagnosedAt = Instant.parse("2026-08-22T18:15:16.123456789Z");
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(
                serviceOrders, technicians, Clock.fixed(diagnosedAt, ZoneOffset.UTC));

        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        serviceOrder.assignDiagnosisAssignee(technician.id());
        serviceOrders.save(serviceOrder);
        technicians.save(technician);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(technician.id(), List.of(
                new DiagnosisItemRequest(UUID.randomUUID(), "Troca de óleo",
                        new MoneyDTO(BigDecimal.TEN, "BRL"), null),
                new DiagnosisItemRequest(UUID.randomUUID(), "Alinhamento",
                        new MoneyDTO(BigDecimal.ONE, "BRL"), null)));

        ServiceOrderResponse response = useCase.execute(serviceOrder.id(), request);

        assertEquals(2, response.executions().size());
        assertEquals(technician.id(), response.executions().get(0).diagnosedByTechnicianId());
        assertEquals(technician.id(), response.executions().get(1).diagnosedByTechnicianId());
        assertEquals(Instant.parse("2026-08-22T18:15:16.123456Z"), response.executions().get(0).diagnosedAt());
        assertEquals(response.executions().get(0).diagnosedAt(), response.executions().get(1).diagnosedAt());
    }

    @Test
    void rejectsDiagnosisWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        technicians.save(technician);
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders, technicians);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(technician.id(), List.of(
                new DiagnosisItemRequest(UUID.randomUUID(), "Troca de óleo",
                        new MoneyDTO(BigDecimal.TEN, "BRL"), null)));

        assertThrows(NoSuchElementException.class, () -> useCase.execute(UUID.randomUUID(), request));
    }

    @Test
    void rejectsDiagnosisWhenAuthorTechnicianDoesNotExistWithoutChangingTheServiceOrder() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders, technicians);
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrders.save(serviceOrder);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(UUID.randomUUID(), List.of(
                new DiagnosisItemRequest(UUID.randomUUID(), "Troca de óleo",
                        new MoneyDTO(BigDecimal.TEN, "BRL"), null)));

        assertThrows(NoSuchElementException.class, () -> useCase.execute(serviceOrder.id(), request));
        assertEquals(0, serviceOrder.serviceExecutions().size());
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

    private static final class InMemoryTechnicianRepository implements TechnicianRepository {
        private final Map<UUID, Technician> byId = new HashMap<>();

        @Override
        public Optional<Technician> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Technician> findAll() {
            return List.copyOf(byId.values());
        }

        @Override
        public void save(Technician technician) {
            byId.put(technician.id(), technician);
        }
    }
}
