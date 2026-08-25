package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception.VehicleNotFoundException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleQueryUseCaseTest {

    @Mock
    private VehicleRepository repository;

    private GetVehicleUseCase getUseCase;
    private ListVehiclesUseCase listUseCase;

    @BeforeEach
    void setUp() {
        getUseCase = new GetVehicleUseCase(repository);
        listUseCase = new ListVehiclesUseCase(repository);
    }

    @Test
    void getsActiveOrArchivedVehicleById() {
        UUID activeId = UUID.randomUUID();
        UUID archivedId = UUID.randomUUID();
        when(repository.findById(activeId)).thenReturn(Optional.of(vehicle(activeId, "ABC1234", true)));
        when(repository.findById(archivedId)).thenReturn(Optional.of(vehicle(archivedId, "ABC1D23", false)));

        VehicleResponse active = getUseCase.execute(activeId);
        VehicleResponse archived = getUseCase.execute(archivedId);

        assertTrue(active.active());
        assertFalse(archived.active());
    }

    @Test
    void rejectsMissingVehicleById() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> getUseCase.execute(id));
    }

    @Test
    void listsEveryActiveVehicleReturnedByTheOperationalQuery() {
        Vehicle first = vehicle(UUID.randomUUID(), "ABC1234", true);
        Vehicle second = vehicle(UUID.randomUUID(), "ABC1D23", true);
        when(repository.findAllActive()).thenReturn(List.of(first, second));

        List<VehicleResponse> responses = listUseCase.execute();

        assertEquals(List.of(first.id(), second.id()), responses.stream().map(VehicleResponse::id).toList());
        assertTrue(responses.stream().allMatch(VehicleResponse::active));
    }

    @Test
    void returnsEmptyListWhenThereAreNoActiveVehicles() {
        when(repository.findAllActive()).thenReturn(List.of());

        assertTrue(listUseCase.execute().isEmpty());
    }

    private static Vehicle vehicle(UUID id, String plate, boolean active) {
        return Vehicle.reconstitute(
                id,
                UUID.randomUUID(),
                new LicensePlate(plate),
                null,
                "Volkswagen",
                "Gol",
                VehicleYear.create(2026, 2026),
                "Prata",
                null,
                active);
    }
}
