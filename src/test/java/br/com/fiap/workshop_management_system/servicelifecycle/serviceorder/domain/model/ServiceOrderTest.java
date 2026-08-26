package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceOrderTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    private ServiceOrder newServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        return serviceOrder;
    }

    private UUID diagnoseWithOneExecution(ServiceOrder serviceOrder) {
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        return serviceOrder.serviceExecutions().get(0).id();
    }

    private UUID authorizeExecution(ServiceOrder serviceOrder, UUID executionId) {
        UUID estimateId = UUID.randomUUID();
        serviceOrder.authorizeExecutionFromEstimate(estimateId, executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        return estimateId;
    }

    @Test
    void newServiceOrderStartsAsReceived() {
        ServiceOrder serviceOrder = newServiceOrder();

        assertEquals(ServiceOrderStatus.RECEIVED, serviceOrder.status());
        assertEquals("Initial assessment", serviceOrder.initialAssessment());
    }

    @Test
    void newServiceOrderDoesNotHaveADiagnosisAssignee() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");

        assertNull(serviceOrder.diagnosisAssigneeId());
        assertThrows(IllegalStateException.class,
                () -> serviceOrder.performDiagnosis(List.of(), UUID.randomUUID(), java.time.Instant.EPOCH));
    }

    @Test
    void newServiceOrderRequiresANonBlankInitialAssessment() {
        assertThrows(InvalidServiceOrderException.class,
                () -> ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, null));
        assertThrows(InvalidServiceOrderException.class,
                () -> ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, ""));
        assertThrows(InvalidServiceOrderException.class,
                () -> ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "   "));
    }

    @Test
    void performDiagnosisMovesStatusToInDiagnosis() {
        ServiceOrder serviceOrder = newServiceOrder();

        diagnoseWithOneExecution(serviceOrder);

        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, serviceOrder.status());
    }

    @Test
    void recordingAvailabilityIsInformationalAndKeepsExecutionPending() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        UUID diagnosisId = serviceOrder.openDiagnosisId();
        StockAvailabilitySnapshot snapshot = new StockAvailabilitySnapshot(
                UUID.randomUUID(), 3, 1, 2, StockAvailabilityStatus.INSUFFICIENT_QUANTITY, Instant.now());

        serviceOrder.recordStockAvailability(diagnosisId, Map.of(executionId, List.of(snapshot)));

        assertEquals(ServiceExecutionStatus.PENDING, serviceOrder.serviceExecutions().getFirst().status());
        assertEquals(snapshot, serviceOrder.serviceExecutions().getFirst().stockAvailability().getFirst());
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, serviceOrder.status());
    }

    @Test
    void cannotOpenTwoDiagnosesAtOnce() {
        ServiceOrder serviceOrder = newServiceOrder();
        diagnoseWithOneExecution(serviceOrder);

        DiagnosisItem secondItem = new DiagnosisItem(UUID.randomUUID(), "Alinhamento", Money.brl(BigDecimal.ONE), List.of());
        assertThrows(IllegalStateException.class,
                () -> serviceOrder.performDiagnosis(List.of(secondItem), UUID.randomUUID(), java.time.Instant.EPOCH));
    }

    @Test
    void authorizingExecutionWithoutPendingStockMovesItToReadyAndClosesTheDiagnosis() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);

        authorizeExecution(serviceOrder, executionId);

        assertEquals(ServiceExecutionStatus.READY, serviceOrder.serviceExecutions().get(0).status());
        assertEquals(ServiceOrderStatus.IN_PROGRESS, serviceOrder.status());
    }

    @Test
    void rf19_confirmingTechnicianAssignmentSetsTheAssignedTechnician() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        authorizeExecution(serviceOrder, executionId);
        UUID technicianId = UUID.randomUUID();

        serviceOrder.confirmTechnicianAssignment(executionId, technicianId);

        assertEquals(technicianId, serviceOrder.serviceExecutions().get(0).assignedTechnicianId());
    }

    @Test
    void rf20_startingExecutionRequiresReadyStatusAndMovesServiceOrderToInProgress() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);

        assertThrows(IllegalStateException.class, () -> serviceOrder.startExecution(executionId));

        authorizeExecution(serviceOrder, executionId);
        serviceOrder.startExecution(executionId);

        assertEquals(ServiceExecutionStatus.IN_PROGRESS, serviceOrder.serviceExecutions().get(0).status());
        assertEquals(ServiceOrderStatus.IN_PROGRESS, serviceOrder.status());
    }

    @Test
    void rf21_updatingProgressRequiresExecutionToBeInProgress() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        authorizeExecution(serviceOrder, executionId);

        assertThrows(IllegalStateException.class, () -> serviceOrder.updateExecutionProgress(executionId, "iniciando"));

        serviceOrder.startExecution(executionId);
        serviceOrder.updateExecutionProgress(executionId, "50% concluído");
    }

    @Test
    void rf22_completingExecutionMovesServiceOrderToCompletedWhenAllExecutionsAreDone() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        authorizeExecution(serviceOrder, executionId);
        serviceOrder.startExecution(executionId);

        serviceOrder.completeExecution(executionId);

        assertEquals(ServiceExecutionStatus.COMPLETED, serviceOrder.serviceExecutions().get(0).status());
        assertEquals(ServiceOrderStatus.COMPLETED, serviceOrder.status());
    }

    @Test
    void rejectedExecutionsAreIgnoredWhenComputingCompletion() {
        ServiceOrder serviceOrder = newServiceOrder();
        DiagnosisItem approvedItem = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        DiagnosisItem rejectedItem = new DiagnosisItem(UUID.randomUUID(), "Polimento", Money.brl(BigDecimal.ONE), List.of());
        serviceOrder.performDiagnosis(List.of(approvedItem, rejectedItem), UUID.randomUUID(), java.time.Instant.EPOCH);

        UUID approvedExecutionId = serviceOrder.serviceExecutions().get(0).id();
        UUID rejectedExecutionId = serviceOrder.serviceExecutions().get(1).id();

        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), approvedExecutionId);
        serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), rejectedExecutionId);
        serviceOrder.startExecution(approvedExecutionId);
        serviceOrder.completeExecution(approvedExecutionId);

        assertEquals(ServiceOrderStatus.COMPLETED, serviceOrder.status());
    }

    @Test
    void allRejectedExecutionsMoveServiceOrderToCompleted() {
        ServiceOrder serviceOrder = newServiceOrder();
        DiagnosisItem firstItem = new DiagnosisItem(
                UUID.randomUUID(), "Alinhamento", Money.brl(BigDecimal.TEN), List.of());
        DiagnosisItem secondItem = new DiagnosisItem(
                UUID.randomUUID(), "Polimento", Money.brl(BigDecimal.ONE), List.of());
        serviceOrder.performDiagnosis(List.of(firstItem, secondItem), UUID.randomUUID(), java.time.Instant.EPOCH);

        serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), serviceOrder.serviceExecutions().get(0).id());
        serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), serviceOrder.serviceExecutions().get(1).id());

        assertEquals(ServiceOrderStatus.COMPLETED, serviceOrder.status());
    }

    @Test
    void rf24_finalizeRequiresCompletedStatusAndVehicleDelivered() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);

        assertThrows(IllegalStateException.class, () -> serviceOrder.finalize(true));

        authorizeExecution(serviceOrder, executionId);
        serviceOrder.startExecution(executionId);
        serviceOrder.completeExecution(executionId);

        assertThrows(IllegalStateException.class, () -> serviceOrder.finalize(false));

        serviceOrder.finalize(true);

        assertEquals(ServiceOrderStatus.DELIVERED, serviceOrder.status());
    }

    @Test
    void deliveredStatusIsPreservedWhenAProjectionConditionChangesLater() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        authorizeExecution(serviceOrder, executionId);
        serviceOrder.startExecution(executionId);
        serviceOrder.completeExecution(executionId);
        serviceOrder.finalize(true);

        serviceOrder.markEstimateSentWithPendingLines();

        assertEquals(ServiceOrderStatus.DELIVERED, serviceOrder.status());
    }

    @Test
    void rf23_sentEstimateWithPendingLinesIsReportedAsAwaitingApproval() {
        ServiceOrder serviceOrder = newServiceOrder();
        diagnoseWithOneExecution(serviceOrder);

        serviceOrder.markEstimateSentWithPendingLines();

        assertEquals(ServiceOrderStatus.AWAITING_APPROVAL, serviceOrder.status());
    }

    @Test
    void rf10_definePriorityChangesThePriorityWhenNotCompletedOrDelivered() {
        ServiceOrder serviceOrder = newServiceOrder();

        serviceOrder.definePriority(Priority.URGENT);

        assertEquals(Priority.URGENT, serviceOrder.priority());
    }

    @Test
    void rf10_definePriorityIsRejectedWhenServiceOrderIsCompleted() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        authorizeExecution(serviceOrder, executionId);
        serviceOrder.startExecution(executionId);
        serviceOrder.completeExecution(executionId);

        assertThrows(IllegalStateException.class, () -> serviceOrder.definePriority(Priority.URGENT));
    }

    @Test
    void rf10_definePriorityIsRejectedWhenServiceOrderIsDelivered() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        authorizeExecution(serviceOrder, executionId);
        serviceOrder.startExecution(executionId);
        serviceOrder.completeExecution(executionId);
        serviceOrder.finalize(true);

        assertThrows(IllegalStateException.class, () -> serviceOrder.definePriority(Priority.URGENT));
    }

    @Test
    void awaitingItemsTakesPrecedenceOverAwaitingApproval() {
        ServiceOrder serviceOrder = newServiceOrder();
        StockRequirement pendingPart = new StockRequirement(
                UUID.randomUUID(), StockItemType.PART, 1, "Filtro de óleo", Money.brl(BigDecimal.TEN), false);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de filtro", Money.brl(BigDecimal.TEN), List.of(pendingPart));
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();

        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.markEstimateSentWithPendingLines();

        assertEquals(ServiceExecutionStatus.AWAITING_ITEMS, serviceOrder.serviceExecutions().get(0).status());
        assertEquals(ServiceOrderStatus.AWAITING_ITEMS, serviceOrder.status());
    }

    @Test
    void stockRequirementsCanOnlyBeAttachedWhileAnExecutionIsPendingAndUnfrozen() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);
        StockRequirement pendingPart = new StockRequirement(
                UUID.randomUUID(), StockItemType.PART, 1, "Correia dentada", Money.brl(BigDecimal.TEN), false);
        serviceOrder.attachStockRequirement(executionId, pendingPart);

        ServiceExecution execution = serviceOrder.serviceExecutions().get(0);
        assertEquals(ServiceExecutionStatus.PENDING, execution.status());
        serviceOrder.freezeStockRequirements(execution.diagnosisId());
        assertThrows(IllegalStateException.class,
                () -> serviceOrder.attachStockRequirement(executionId, pendingPart));
    }

    @Test
    void rf12_attachingStockRequirementToAnUnknownExecutionThrows() {
        ServiceOrder serviceOrder = newServiceOrder();
        diagnoseWithOneExecution(serviceOrder);
        StockRequirement pendingPart = new StockRequirement(
                UUID.randomUUID(), StockItemType.PART, 1, "Correia dentada", Money.brl(BigDecimal.TEN), false);

        assertThrows(NoSuchElementException.class,
                () -> serviceOrder.attachStockRequirement(UUID.randomUUID(), pendingPart));
    }

    /**
     * RF18 - registrar um novo diagnóstico (reparo adicional) durante a execução: já funciona hoje
     * com o código existente de performDiagnosis/RF11 - não olha o status das ServiceExecution de
     * lotes anteriores, apenas se o diagnóstico anterior já foi totalmente decidido (openDiagnosisId
     * volta a null).
     */
    @Test
    void rf18_canRegisterANewDiagnosisWhileAnEarlierExecutionIsInProgress() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID firstExecutionId = diagnoseWithOneExecution(serviceOrder);
        authorizeExecution(serviceOrder, firstExecutionId);
        serviceOrder.startExecution(firstExecutionId);
        assertEquals(ServiceExecutionStatus.IN_PROGRESS, serviceOrder.serviceExecutions().get(0).status());
        assertNull(serviceOrder.openDiagnosisId());

        DiagnosisItem additionalRepair = new DiagnosisItem(
                UUID.randomUUID(), "Reparo adicional", Money.brl(BigDecimal.TEN), List.of());
        UUID secondDiagnosisId = serviceOrder.performDiagnosis(
                List.of(additionalRepair), UUID.randomUUID(), java.time.Instant.EPOCH);

        assertEquals(secondDiagnosisId, serviceOrder.openDiagnosisId());
        assertEquals(2, serviceOrder.serviceExecutions().size());
        assertEquals(ServiceExecutionStatus.PENDING, serviceOrder.serviceExecutions().get(1).status());
        assertEquals(ServiceOrderStatus.IN_PROGRESS, serviceOrder.status());
    }
}
