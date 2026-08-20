package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.AssignTechnicianRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Specialty;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssignTechnicianUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    @Test
    void assignsExistingTechnicianToAnAuthorizedExecution() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        AssignTechnicianUseCase useCase = new AssignTechnicianUseCase(serviceOrders, technicians);

        ServiceOrder serviceOrder = newAuthorizedServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        technicians.save(technician);

        ServiceOrderResponse response = useCase.execute(
                serviceOrder.id(), executionId, new AssignTechnicianRequest(technician.id()));

        assertEquals(technician.id(), response.executions().get(0).assignedTechnicianId());
    }

    @Test
    void rejectsAssignmentWhenTechnicianDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        AssignTechnicianUseCase useCase = new AssignTechnicianUseCase(serviceOrders, technicians);

        ServiceOrder serviceOrder = newAuthorizedServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrders.save(serviceOrder);
        UUID unknownTechnicianId = UUID.randomUUID();

        assertThrows(NoSuchElementException.class, () -> useCase.execute(
                serviceOrder.id(), executionId, new AssignTechnicianRequest(unknownTechnicianId)));
    }

    @Test
    void rejectsAssignmentWhenServiceOrderDoesNotExist() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryTechnicianRepository technicians = new InMemoryTechnicianRepository();
        AssignTechnicianUseCase useCase = new AssignTechnicianUseCase(serviceOrders, technicians);
        Technician technician = Technician.create("Carlos Silva", Set.of(Specialty.MECHANICAL));
        technicians.save(technician);

        assertThrows(NoSuchElementException.class, () -> useCase.execute(
                UUID.randomUUID(), UUID.randomUUID(), new AssignTechnicianRequest(technician.id())));
    }

    private ServiceOrder newAuthorizedServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item));
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
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
