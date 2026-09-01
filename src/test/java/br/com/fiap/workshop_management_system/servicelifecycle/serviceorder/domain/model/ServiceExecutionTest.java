package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceExecutionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-28T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-28T11:30:00Z");

    private ServiceOrder serviceOrderWithOneExecution() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        return serviceOrder;
    }

    @Test
    void cannotAssignTechnicianToARejectedExecution() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), executionId);

        assertThrows(IllegalStateException.class,
                () -> serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID()));
    }

    @Test
    void executionWithPendingStockRequirementStaysAwaitingItemsUntilItsReservationIsConfirmed() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        StockRequirement pendingPart = new StockRequirement(
                UUID.randomUUID(), StockItemType.PART, 1, "Pastilha de freio", Money.brl(BigDecimal.TEN), false);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de pastilha", Money.brl(BigDecimal.TEN), List.of(pendingPart));
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();

        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        assertEquals(ServiceExecutionStatus.AWAITING_ITEMS, serviceOrder.serviceExecutions().get(0).status());
        assertThrows(IllegalStateException.class, () -> serviceOrder.startExecution(executionId, java.time.Instant.now()));

        UUID reservationId = UUID.randomUUID();
        serviceOrder.confirmStockReservation(executionId, reservationId);

        assertEquals(ServiceExecutionStatus.READY, serviceOrder.serviceExecutions().get(0).status());
        assertEquals(reservationId, serviceOrder.serviceExecutions().get(0).stockReservationId());
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, java.time.Instant.now());
        assertEquals(ServiceExecutionStatus.IN_PROGRESS, serviceOrder.serviceExecutions().get(0).status());
    }

    @Test
    void cannotCompleteAnExecutionThatHasNotStarted() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);

        assertThrows(IllegalStateException.class, () -> serviceOrder.completeExecution(executionId, java.time.Instant.now()));
    }

    @Test
    void cannotStartAReadyExecutionWithoutAnAssignedTechnician() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);

        assertThrows(IllegalStateException.class, () -> serviceOrder.startExecution(executionId, java.time.Instant.now()));

        assertEquals(ServiceExecutionStatus.READY, serviceOrder.serviceExecutions().get(0).status());
    }

    @Test
    void recordsImmutableStartAndCompletionInstantsFromStatusTransitions() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());

        serviceOrder.startExecution(executionId, STARTED_AT);
        serviceOrder.completeExecution(executionId, COMPLETED_AT);

        ServiceExecution execution = serviceOrder.serviceExecutions().getFirst();
        assertEquals(STARTED_AT, execution.startedAt());
        assertEquals(COMPLETED_AT, execution.completedAt());
    }

    @Test
    void acceptsZeroExecutionDuration() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, STARTED_AT);

        serviceOrder.completeExecution(executionId, STARTED_AT);

        assertEquals(STARTED_AT, serviceOrder.serviceExecutions().getFirst().completedAt());
    }

    @Test
    void rejectsCompletionBeforeStartWithoutChangingTheExecution() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, STARTED_AT);

        assertThrows(
                IllegalStateException.class,
                () -> serviceOrder.completeExecution(executionId, STARTED_AT.minusSeconds(1)));

        ServiceExecution execution = serviceOrder.serviceExecutions().getFirst();
        assertEquals(ServiceExecutionStatus.IN_PROGRESS, execution.status());
        assertNull(execution.completedAt());
    }

    @Test
    void legacyReconstitutionKeepsExecutionTimestampsAbsent() {
        ServiceExecution execution = ServiceExecution.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Troca de óleo",
                Money.brl(BigDecimal.TEN),
                ServiceExecutionStatus.COMPLETED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                List.of());

        assertNull(execution.startedAt());
        assertNull(execution.completedAt());
    }

    @Test
    void rejectsReconstitutionWithCompletionButNoStartTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> ServiceExecution.reconstitute(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Troca de óleo",
                Money.brl(BigDecimal.TEN),
                ServiceExecutionStatus.COMPLETED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.EPOCH,
                null,
                COMPLETED_AT,
                false,
                null,
                List.of(),
                List.of()));
    }

    @Test
    void canUpdateProgressOfAnInProgressExecution() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, java.time.Instant.now());

        serviceOrder.updateExecutionProgress(executionId, "Peça trocada, aguardando teste");

        assertEquals(ServiceExecutionStatus.IN_PROGRESS, serviceOrder.serviceExecutions().get(0).status());
    }

    @Test
    void cannotUpdateProgressOfAnExecutionThatHasNotStarted() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();

        assertThrows(IllegalStateException.class,
                () -> serviceOrder.updateExecutionProgress(executionId, "nota"));
    }

    @Test
    void cannotUpdateProgressOfAReadyExecutionThatHasNotStartedYet() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);

        assertThrows(IllegalStateException.class,
                () -> serviceOrder.updateExecutionProgress(executionId, "nota"));
    }

    private StockRequirement newStockRequirement() {
        return new StockRequirement(
                UUID.randomUUID(), StockItemType.PART, 1, "Correia dentada", Money.brl(BigDecimal.TEN), false);
    }

    @Test
    void rf12_cannotAttachStockRequirementToACompletedExecution() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, java.time.Instant.now());
        serviceOrder.completeExecution(executionId, java.time.Instant.now());

        assertThrows(IllegalStateException.class,
                () -> serviceOrder.attachStockRequirement(executionId, newStockRequirement()));
    }

    @Test
    void rf12_cannotAttachStockRequirementToARejectedExecution() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), executionId);

        assertThrows(IllegalStateException.class,
                () -> serviceOrder.attachStockRequirement(executionId, newStockRequirement()));
    }

    @Test
    void cannotAttachStockRequirementAfterAuthorization() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        assertEquals(ServiceExecutionStatus.READY, serviceOrder.serviceExecutions().get(0).status());

        assertThrows(IllegalStateException.class,
                () -> serviceOrder.attachStockRequirement(executionId, newStockRequirement()));
    }

    @Test
    void rf12_attachingStockRequirementDoesNotChangeStatusOfAPendingExecution() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();

        serviceOrder.attachStockRequirement(executionId, newStockRequirement());

        assertEquals(ServiceExecutionStatus.PENDING, serviceOrder.serviceExecutions().get(0).status());
    }

    @Test
    void cannotAttachStockRequirementToAnInProgressExecution() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, java.time.Instant.now());

        assertThrows(IllegalStateException.class,
                () -> serviceOrder.attachStockRequirement(executionId, newStockRequirement()));
    }

    @Test
    void confirmsAReservationOnlyOnceAndRejectsADifferentReservationId() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        StockRequirement requirement = newStockRequirement();
        serviceOrder.performDiagnosis(
                List.of(new DiagnosisItem(
                        UUID.randomUUID(), "Troca de correia", Money.brl(BigDecimal.TEN), List.of(requirement))),
                UUID.randomUUID(),
                java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        UUID reservationId = UUID.randomUUID();

        serviceOrder.confirmStockReservation(executionId, reservationId);
        serviceOrder.confirmStockReservation(executionId, reservationId);

        ServiceExecution execution = serviceOrder.serviceExecutions().get(0);
        assertEquals(ServiceExecutionStatus.READY, execution.status());
        assertEquals(reservationId, execution.stockReservationId());
        assertEquals(true, execution.stockRequirements().get(0).reserved());
        assertThrows(IllegalStateException.class,
                () -> serviceOrder.confirmStockReservation(executionId, UUID.randomUUID()));
    }
}
