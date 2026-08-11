package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceOrderTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    private ServiceOrder newServiceOrder() {
        return ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
    }

    private UUID diagnoseWithOneExecution(ServiceOrder serviceOrder) {
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item));
        return serviceOrder.serviceExecutions().get(0).id();
    }

    private UUID authorizeExecution(ServiceOrder serviceOrder, UUID executionId) {
        UUID estimateId = UUID.randomUUID();
        serviceOrder.authorizeExecutionFromEstimate(estimateId, executionId);
        return estimateId;
    }

    @Test
    void newServiceOrderStartsAsReceived() {
        ServiceOrder serviceOrder = newServiceOrder();

        assertEquals(ServiceOrderStatus.RECEIVED, serviceOrder.status());
    }

    @Test
    void performDiagnosisMovesStatusToInDiagnosis() {
        ServiceOrder serviceOrder = newServiceOrder();

        diagnoseWithOneExecution(serviceOrder);

        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, serviceOrder.status());
    }

    @Test
    void cannotOpenTwoDiagnosesAtOnce() {
        ServiceOrder serviceOrder = newServiceOrder();
        diagnoseWithOneExecution(serviceOrder);

        DiagnosisItem secondItem = new DiagnosisItem(UUID.randomUUID(), "Alinhamento", Money.brl(BigDecimal.ONE), List.of());
        assertThrows(IllegalStateException.class, () -> serviceOrder.performDiagnosis(List.of(secondItem)));
    }

    @Test
    void authorizingExecutionWithoutPendingStockMovesItToReadyAndClosesTheDiagnosis() {
        ServiceOrder serviceOrder = newServiceOrder();
        UUID executionId = diagnoseWithOneExecution(serviceOrder);

        authorizeExecution(serviceOrder, executionId);

        assertEquals(ServiceExecutionStatus.READY, serviceOrder.serviceExecutions().get(0).status());
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
        serviceOrder.performDiagnosis(List.of(approvedItem, rejectedItem));

        UUID approvedExecutionId = serviceOrder.serviceExecutions().get(0).id();
        UUID rejectedExecutionId = serviceOrder.serviceExecutions().get(1).id();

        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), approvedExecutionId);
        serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), rejectedExecutionId);
        serviceOrder.startExecution(approvedExecutionId);
        serviceOrder.completeExecution(approvedExecutionId);

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
    void rf23_sentEstimateWithPendingLinesIsReportedAsAwaitingApproval() {
        ServiceOrder serviceOrder = newServiceOrder();
        diagnoseWithOneExecution(serviceOrder);

        serviceOrder.markEstimateSentWithPendingLines();

        assertEquals(ServiceOrderStatus.AWAITING_APPROVAL, serviceOrder.status());
    }

    @Test
    void awaitingPartTakesPrecedenceOverAwaitingApproval() {
        ServiceOrder serviceOrder = newServiceOrder();
        StockRequirement pendingPart = new StockRequirement(
                UUID.randomUUID(), StockItemType.PART, 1, "Filtro de óleo", Money.brl(BigDecimal.TEN), false);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de filtro", Money.brl(BigDecimal.TEN), List.of(pendingPart));
        serviceOrder.performDiagnosis(List.of(item));
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();

        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.markEstimateSentWithPendingLines();

        assertEquals(ServiceExecutionStatus.AWAITING_PART, serviceOrder.serviceExecutions().get(0).status());
        assertEquals(ServiceOrderStatus.AWAITING_PART, serviceOrder.status());
    }
}