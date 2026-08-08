package br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceExecutionTest {

    private ServiceOrder serviceOrderWithOneExecution() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015));
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item));
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
    void executionWithPendingStockRequirementStaysAwaitingPartUntilReserved() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015));
        StockRequirement pendingPart = new StockRequirement(
                UUID.randomUUID(), StockItemType.PART, 1, "Pastilha de freio", Money.brl(BigDecimal.TEN), false);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de pastilha", Money.brl(BigDecimal.TEN), List.of(pendingPart));
        serviceOrder.performDiagnosis(List.of(item));
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();

        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        assertEquals(ServiceExecutionStatus.AWAITING_PART, serviceOrder.serviceExecutions().get(0).status());
        assertThrows(IllegalStateException.class, () -> serviceOrder.startExecution(executionId));

        serviceOrder.applyStockReservation(executionId, pendingPart.stockItemId());

        assertEquals(ServiceExecutionStatus.READY, serviceOrder.serviceExecutions().get(0).status());
        serviceOrder.startExecution(executionId);
        assertEquals(ServiceExecutionStatus.IN_PROGRESS, serviceOrder.serviceExecutions().get(0).status());
    }

    @Test
    void cannotCompleteAnExecutionThatHasNotStarted() {
        ServiceOrder serviceOrder = serviceOrderWithOneExecution();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);

        assertThrows(IllegalStateException.class, () -> serviceOrder.completeExecution(executionId));
    }
}