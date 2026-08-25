package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.DiagnosisItemRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.MoneyDTO;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceArchivedForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceNotFoundForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CatalogServiceEligibility;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibilityPort;
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
import java.util.ArrayList;
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
        RecordingCatalogServiceEligibilityPort catalogServices = new RecordingCatalogServiceEligibilityPort();
        Instant diagnosedAt = Instant.parse("2026-08-22T18:15:16.123456789Z");
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(
                serviceOrders, technicians, catalogServices, Clock.fixed(diagnosedAt, ZoneOffset.UTC));

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
        assertEquals(request.items().stream().map(DiagnosisItemRequest::catalogServiceId).sorted().toList(),
                catalogServices.checkedIds());
    }

    @Test
    void rejectsDiagnosisWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        RecordingCatalogServiceEligibilityPort catalogServices = new RecordingCatalogServiceEligibilityPort();
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        technicians.save(technician);
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders, technicians, catalogServices);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(technician.id(), List.of(
                new DiagnosisItemRequest(UUID.randomUUID(), "Troca de óleo",
                        new MoneyDTO(BigDecimal.TEN, "BRL"), null)));

        assertThrows(NoSuchElementException.class, () -> useCase.execute(UUID.randomUUID(), request));
        assertEquals(List.of(), catalogServices.checkedIds());
    }

    @Test
    void rejectsDiagnosisWhenAuthorTechnicianDoesNotExistWithoutChangingTheServiceOrder() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        RecordingCatalogServiceEligibilityPort catalogServices = new RecordingCatalogServiceEligibilityPort();
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders, technicians, catalogServices);
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrders.save(serviceOrder);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(UUID.randomUUID(), List.of(
                new DiagnosisItemRequest(UUID.randomUUID(), "Troca de óleo",
                        new MoneyDTO(BigDecimal.TEN, "BRL"), null)));

        assertThrows(NoSuchElementException.class, () -> useCase.execute(serviceOrder.id(), request));
        assertEquals(0, serviceOrder.serviceExecutions().size());
        assertEquals(List.of(), catalogServices.checkedIds());
    }

    @Test
    void checksDistinctCatalogServicesInUuidOrder() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        RecordingCatalogServiceEligibilityPort catalogServices = new RecordingCatalogServiceEligibilityPort();
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders, technicians, catalogServices);
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        serviceOrder.assignDiagnosisAssignee(technician.id());
        serviceOrders.save(serviceOrder);
        technicians.save(technician);
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PerformDiagnosisRequest request = new PerformDiagnosisRequest(technician.id(), List.of(
                item(second, "Alinhamento"),
                item(first, "Troca de óleo"),
                item(second, "Alinhamento premium")));

        useCase.execute(serviceOrder.id(), request);

        assertEquals(List.of(first, second), catalogServices.checkedIds());
        assertEquals(3, serviceOrder.serviceExecutions().size());
    }

    @Test
    void rejectsArchivedCatalogServiceBeforeChangingOrSavingTheServiceOrder() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        RecordingCatalogServiceEligibilityPort catalogServices = new RecordingCatalogServiceEligibilityPort();
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders, technicians, catalogServices);
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        serviceOrder.assignDiagnosisAssignee(technician.id());
        serviceOrders.save(serviceOrder);
        serviceOrders.resetSaveCount();
        technicians.save(technician);
        UUID catalogServiceId = UUID.randomUUID();
        catalogServices.set(catalogServiceId, CatalogServiceEligibility.ARCHIVED);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(
                technician.id(), List.of(item(catalogServiceId, "Troca de óleo")));

        assertThrows(CatalogServiceArchivedForNewWorkException.class,
                () -> useCase.execute(serviceOrder.id(), request));
        assertEquals(0, serviceOrder.serviceExecutions().size());
        assertEquals(0, serviceOrders.saveCount());
    }

    @Test
    void rejectsMissingCatalogServiceBeforeChangingOrSavingTheServiceOrder() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        RecordingCatalogServiceEligibilityPort catalogServices = new RecordingCatalogServiceEligibilityPort();
        PerformDiagnosisUseCase useCase = new PerformDiagnosisUseCase(serviceOrders, technicians, catalogServices);
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        serviceOrder.assignDiagnosisAssignee(technician.id());
        serviceOrders.save(serviceOrder);
        serviceOrders.resetSaveCount();
        technicians.save(technician);
        UUID catalogServiceId = UUID.randomUUID();
        catalogServices.set(catalogServiceId, CatalogServiceEligibility.NOT_FOUND);

        PerformDiagnosisRequest request = new PerformDiagnosisRequest(
                technician.id(), List.of(item(catalogServiceId, "Troca de óleo")));

        assertThrows(CatalogServiceNotFoundForNewWorkException.class,
                () -> useCase.execute(serviceOrder.id(), request));
        assertEquals(0, serviceOrder.serviceExecutions().size());
        assertEquals(0, serviceOrders.saveCount());
    }

    private static DiagnosisItemRequest item(UUID catalogServiceId, String name) {
        return new DiagnosisItemRequest(
                catalogServiceId, name, new MoneyDTO(BigDecimal.TEN, "BRL"), null);
    }

    private static final class InMemoryServiceOrderRepository implements ServiceOrderRepository {
        private final Map<UUID, ServiceOrder> byId = new HashMap<>();
        private int saveCount;

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            byId.put(serviceOrder.id(), serviceOrder);
            saveCount++;
        }

        private void resetSaveCount() {
            saveCount = 0;
        }

        private int saveCount() {
            return saveCount;
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

    private static final class RecordingCatalogServiceEligibilityPort implements CatalogServiceEligibilityPort {
        private final Map<UUID, CatalogServiceEligibility> eligibilityById = new HashMap<>();
        private final List<UUID> checkedIds = new ArrayList<>();

        @Override
        public CatalogServiceEligibility checkForNewWork(UUID catalogServiceId) {
            checkedIds.add(catalogServiceId);
            return eligibilityById.getOrDefault(catalogServiceId, CatalogServiceEligibility.ACTIVE);
        }

        private void set(UUID catalogServiceId, CatalogServiceEligibility eligibility) {
            eligibilityById.put(catalogServiceId, eligibility);
        }

        private List<UUID> checkedIds() {
            return List.copyOf(checkedIds);
        }
    }
}
