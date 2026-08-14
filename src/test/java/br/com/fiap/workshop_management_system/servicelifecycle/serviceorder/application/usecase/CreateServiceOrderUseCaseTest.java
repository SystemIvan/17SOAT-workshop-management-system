package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.VehicleSnapshotRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.TechnicianNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Specialty;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CreateServiceOrderUseCaseTest {

    private final ServiceOrderRepository repository = mock(ServiceOrderRepository.class);
    private final TechnicianRepository technicianRepository = mock(TechnicianRepository.class);
    private final TechnicianNotificationPort technicianNotificationPort = mock(TechnicianNotificationPort.class);
    private final CreateServiceOrderUseCase useCase =
            new CreateServiceOrderUseCase(repository, technicianRepository, technicianNotificationPort);

    private final CreateServiceOrderRequest request = new CreateServiceOrderRequest(
            UUID.randomUUID(), UUID.randomUUID(),
            new VehicleSnapshotRequest("ABC1D23", "Fiat", "Uno", 2015), null);

    private Technician technician(String name, java.util.function.Consumer<Technician> transition) {
        Technician technician = Technician.create(name, Set.of(Specialty.MECHANICAL));
        transition.accept(technician);
        return technician;
    }

    @Test
    void notifiesEveryNonInactiveTechnicianAfterCreatingTheServiceOrder() {
        Technician available = technician("Available Tech", t -> { });
        Technician busy = technician("Busy Tech", Technician::markBusy);
        Technician inactive = technician("Inactive Tech", Technician::deactivate);
        when(technicianRepository.findAll()).thenReturn(List.of(available, busy, inactive));

        ServiceOrderResponse response = useCase.execute(request);

        verify(technicianNotificationPort).notifyServiceOrderCreated(response.id(), available.id());
        verify(technicianNotificationPort).notifyServiceOrderCreated(response.id(), busy.id());
        verify(technicianNotificationPort, never()).notifyServiceOrderCreated(any(), eq(inactive.id()));
    }

    @Test
    void createsTheServiceOrderEvenWhenThereAreNoActiveTechnicians() {
        Technician inactive = technician("Inactive Tech", Technician::deactivate);
        when(technicianRepository.findAll()).thenReturn(List.of(inactive));

        ServiceOrderResponse response = useCase.execute(request);

        assertNotNull(response.id());
        verifyNoInteractions(technicianNotificationPort);
    }

    @Test
    void aNotificationFailureForOneTechnicianDoesNotPreventNotifyingTheOthers() {
        Technician failing = technician("Failing Tech", t -> { });
        Technician succeeding = technician("Succeeding Tech", t -> { });
        when(technicianRepository.findAll()).thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("delivery failed"))
                .when(technicianNotificationPort).notifyServiceOrderCreated(any(), eq(failing.id()));

        ServiceOrderResponse response = useCase.execute(request);

        verify(technicianNotificationPort).notifyServiceOrderCreated(response.id(), failing.id());
        verify(technicianNotificationPort).notifyServiceOrderCreated(response.id(), succeeding.id());
    }
}
