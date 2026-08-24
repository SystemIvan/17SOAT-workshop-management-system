package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        UUID diagnosedByTechnicianId = UUID.randomUUID();
        java.time.Instant diagnosedAt = java.time.Instant.parse("2026-08-22T18:00:00.123456Z");

        UUID serviceOrderId = transactionTemplate.execute(status -> {
            ServiceOrder serviceOrder = ServiceOrder.create(
                    UUID.randomUUID(), UUID.randomUUID(),
                    new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
            serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
            DiagnosisItem item = new DiagnosisItem(
                    UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
            serviceOrder.performDiagnosis(List.of(item), diagnosedByTechnicianId, diagnosedAt);
            UUID executionId = serviceOrder.serviceExecutions().get(0).id();
            serviceOrder.confirmTechnicianAssignment(executionId, technicianId);

            repository.save(serviceOrder);
            entityManager.flush();
            entityManager.clear();
            return serviceOrder.id();
        });

        transactionTemplate.executeWithoutResult(status -> {
            Optional<ServiceOrder> reloaded = repository.findById(serviceOrderId);
            var execution = reloaded.orElseThrow().serviceExecutions().getFirst();
            assertEquals(technicianId, execution.assignedTechnicianId());
            assertEquals(diagnosedByTechnicianId, execution.diagnosedByTechnicianId());
            assertEquals(diagnosedAt, execution.diagnosedAt());
        });
    }

    @Test
    void serializesConcurrentFindsForUpdateOnTheSameServiceOrder() throws Exception {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID serviceOrderId = transactionTemplate.execute(status -> {
            ServiceOrder serviceOrder = ServiceOrder.create(
                    UUID.randomUUID(), UUID.randomUUID(),
                    new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
            repository.save(serviceOrder);
            return serviceOrder.id();
        });
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        CountDownLatch secondTransactionStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstTransaction = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                repository.findByIdForUpdate(serviceOrderId).orElseThrow();
                firstLockAcquired.countDown();
                await(releaseFirstLock);
            }));
            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS));

            Future<?> secondTransaction = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                secondTransactionStarted.countDown();
                repository.findByIdForUpdate(serviceOrderId).orElseThrow();
            }));
            assertTrue(secondTransactionStarted.await(5, TimeUnit.SECONDS));
            assertFalse(secondTransaction.isDone());

            releaseFirstLock.countDown();
            firstTransaction.get(5, TimeUnit.SECONDS);
            secondTransaction.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the concurrent transaction", exception);
        }
    }

    @Test
    void persistsFrozenRequirementsAndTheStockReservationReference() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID reservationId = UUID.randomUUID();

        UUID serviceOrderId = transactionTemplate.execute(status -> {
            ServiceOrder serviceOrder = ServiceOrder.create(
                    UUID.randomUUID(), UUID.randomUUID(),
                    new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
            serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
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
                            false)))), UUID.randomUUID(), java.time.Instant.EPOCH);
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

    @Test
    void persistsAndReloadsInitialAssessment() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        String initialAssessment = "Vibração relatada ao frear";

        UUID serviceOrderId = transactionTemplate.execute(status -> {
            ServiceOrder serviceOrder = ServiceOrder.create(
                    UUID.randomUUID(), UUID.randomUUID(),
                    new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), initialAssessment);
            repository.save(serviceOrder);
            entityManager.flush();
            entityManager.clear();
            return serviceOrder.id();
        });

        transactionTemplate.executeWithoutResult(status -> assertEquals(
                initialAssessment, repository.findById(serviceOrderId).orElseThrow().initialAssessment()));
    }

    @Test
    void reconstitutesLegacyServiceOrderWithNullInitialAssessment() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID serviceOrderId = UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            entityManager.persist(new ServiceOrderJpaEntity(
                    serviceOrderId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "ABC1D23",
                    "Fiat",
                    "Uno",
                    2015,
                    null,
                    null,
                    Priority.NORMAL,
                    ServiceOrderStatus.RECEIVED,
                    null,
                    false,
                    Set.of()));
            entityManager.flush();
            entityManager.clear();
        });

        transactionTemplate.executeWithoutResult(status -> assertNull(
                repository.findById(serviceOrderId).orElseThrow().initialAssessment()));
    }
}
