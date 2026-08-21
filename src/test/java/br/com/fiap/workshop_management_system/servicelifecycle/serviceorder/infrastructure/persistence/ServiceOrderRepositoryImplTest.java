package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RF19 - confirms {@code assigned_technician_id} round-trips through a real JPA persistence context,
 * not just the in-memory aggregate returned right after {@code save}.
 */
@SpringBootTest
class ServiceOrderRepositoryImplTest {

    @Autowired
    private ServiceOrderRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsAndReloadsAssignedTechnicianId() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID technicianId = UUID.randomUUID();

        UUID serviceOrderId = transactionTemplate.execute(status -> {
            ServiceOrder serviceOrder = ServiceOrder.create(
                    UUID.randomUUID(), UUID.randomUUID(),
                    new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015));
            DiagnosisItem item = new DiagnosisItem(
                    UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
            serviceOrder.performDiagnosis(List.of(item));
            UUID executionId = serviceOrder.serviceExecutions().get(0).id();
            serviceOrder.confirmTechnicianAssignment(executionId, technicianId);

            repository.save(serviceOrder);
            entityManager.flush();
            entityManager.clear();
            return serviceOrder.id();
        });

        transactionTemplate.executeWithoutResult(status -> {
            Optional<ServiceOrder> reloaded = repository.findById(serviceOrderId);
            assertEquals(technicianId, reloaded.orElseThrow().serviceExecutions().get(0).assignedTechnicianId());
        });
    }

    @Test
    void persistsFrozenRequirementsAndTheStockReservationReference() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID reservationId = UUID.randomUUID();

        UUID serviceOrderId = transactionTemplate.execute(status -> {
            ServiceOrder serviceOrder = ServiceOrder.create(
                    UUID.randomUUID(), UUID.randomUUID(),
                    new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015));
            serviceOrder.performDiagnosis(List.of(new DiagnosisItem(
                    UUID.randomUUID(),
                    "Troca de óleo",
                    Money.brl(BigDecimal.TEN),
                    List.of(new StockRequirement(
                            UUID.randomUUID(),
                            StockItemType.PART,
                            1,
                            "Filtro",
                            Money.brl(BigDecimal.ONE),
                            false)))));
            var execution = serviceOrder.serviceExecutions().getFirst();
            serviceOrder.freezeStockRequirements(execution.diagnosisId());
            serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), execution.id());
            serviceOrder.confirmStockReservation(execution.id(), reservationId);

            repository.save(serviceOrder);
            entityManager.flush();
            entityManager.clear();
            return serviceOrder.id();
        });

        transactionTemplate.executeWithoutResult(status -> {
            var execution = repository.findById(serviceOrderId).orElseThrow().serviceExecutions().getFirst();
            assertTrue(execution.stockRequirementsFrozen());
            assertEquals(reservationId, execution.stockReservationId());
        });
    }
}
