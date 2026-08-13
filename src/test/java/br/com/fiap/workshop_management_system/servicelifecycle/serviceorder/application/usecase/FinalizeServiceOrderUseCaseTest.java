package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FinalizeServiceOrderUseCaseTest {

    private final ServiceOrderRepository repository = mock(ServiceOrderRepository.class);
    private final CustomerNotificationPort customerNotificationPort = mock(CustomerNotificationPort.class);
    private final FinalizeServiceOrderUseCase useCase =
            new FinalizeServiceOrderUseCase(repository, customerNotificationPort);

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    private ServiceOrder completedServiceOrder(UUID customerId) {
        ServiceOrder serviceOrder = ServiceOrder.create(customerId, UUID.randomUUID(), vehicleSnapshot);
        DiagnosisItem item = new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of());
        serviceOrder.performDiagnosis(List.of(item));
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        serviceOrder.startExecution(executionId);
        serviceOrder.completeExecution(executionId);
        return serviceOrder;
    }

    @Test
    void notifiesCustomerAfterSuccessfullyFinalizingServiceOrder() {
        UUID customerId = UUID.randomUUID();
        ServiceOrder serviceOrder = completedServiceOrder(customerId);
        when(repository.findById(serviceOrder.id())).thenReturn(Optional.of(serviceOrder));

        useCase.execute(serviceOrder.id(), new FinalizeServiceOrderRequest(true));

        verify(customerNotificationPort).notifyServiceOrderFinalized(serviceOrder.id(), customerId);
    }

    @Test
    void doesNotNotifyWhenFinalizePreconditionsAreNotMet() {
        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        when(repository.findById(serviceOrder.id())).thenReturn(Optional.of(serviceOrder));

        assertThrows(IllegalStateException.class,
                () -> useCase.execute(serviceOrder.id(), new FinalizeServiceOrderRequest(true)));

        verifyNoInteractions(customerNotificationPort);
    }

    @Test
    void finalizeSucceedsEvenWhenNotificationFails() {
        UUID customerId = UUID.randomUUID();
        ServiceOrder serviceOrder = completedServiceOrder(customerId);
        when(repository.findById(serviceOrder.id())).thenReturn(Optional.of(serviceOrder));
        doThrow(new RuntimeException("delivery failed"))
                .when(customerNotificationPort).notifyServiceOrderFinalized(any(), any());

        ServiceOrderResponse response = useCase.execute(serviceOrder.id(), new FinalizeServiceOrderRequest(true));

        assertEquals(ServiceOrderStatus.DELIVERED, response.status());
        verify(repository).save(serviceOrder);
    }
}
