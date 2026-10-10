package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Customer notification for the "Entregue" transition is no longer sent from this use case (RF52 consolidated
 * RF33 into the generic status-change flow); see ServiceOrderStatusChangedNotificationListenerTest.
 */
class FinalizeServiceOrderUseCaseTest {

    private final ServiceOrderRepository repository = mock(ServiceOrderRepository.class);
    private final FinalizeServiceOrderUseCase useCase = new FinalizeServiceOrderUseCase(repository);

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    private ServiceOrder completedServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.confirmTechnicianAssignment(executionId, UUID.randomUUID());
        serviceOrder.startExecution(executionId, java.time.Instant.now());
        serviceOrder.completeExecution(executionId, java.time.Instant.now());
        return serviceOrder;
    }

    @Test
    void finalizesAndPersistsACompletedServiceOrder() {
        ServiceOrder serviceOrder = completedServiceOrder();
        when(repository.findById(serviceOrder.id())).thenReturn(Optional.of(serviceOrder));

        ServiceOrderResponse response = useCase.execute(serviceOrder.id(), new FinalizeServiceOrderRequest(true));

        assertEquals(ServiceOrderStatus.DELIVERED, response.status());
        verify(repository).save(serviceOrder);
    }

    @Test
    void rejectsFinalizingWhenServiceOrderIsNotCompleted() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot, "Initial assessment");
        when(repository.findById(serviceOrder.id())).thenReturn(Optional.of(serviceOrder));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(serviceOrder.id(), new FinalizeServiceOrderRequest(true)));

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsFinalizingWhenVehicleWasNotDelivered() {
        ServiceOrder serviceOrder = completedServiceOrder();
        when(repository.findById(serviceOrder.id())).thenReturn(Optional.of(serviceOrder));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(serviceOrder.id(), new FinalizeServiceOrderRequest(false)));

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsFinalizingWhenServiceOrderDoesNotExist() {
        UUID serviceOrderId = UUID.randomUUID();
        when(repository.findById(serviceOrderId)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> useCase.execute(serviceOrderId, new FinalizeServiceOrderRequest(true)));

        verify(repository, never()).save(any());
    }
}
