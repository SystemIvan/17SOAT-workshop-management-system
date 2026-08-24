package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailability;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckVehicleAvailabilityUseCaseTest {

    private final VehicleRepository repository = mock(VehicleRepository.class);
    private final CheckVehicleAvailabilityUseCase useCase = new CheckVehicleAvailabilityUseCase(repository);

    @Test
    void reportsActiveVehicle() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle(id, true)));

        assertEquals(VehicleAvailability.ACTIVE, useCase.checkForNewWork(id));
    }

    @Test
    void reportsArchivedVehicle() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle(id, false)));

        assertEquals(VehicleAvailability.ARCHIVED, useCase.checkForNewWork(id));
    }

    @Test
    void reportsMissingVehicle() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertEquals(VehicleAvailability.NOT_FOUND, useCase.checkForNewWork(id));
    }

    private static Vehicle vehicle(UUID id, boolean active) {
        return Vehicle.reconstitute(
                id,
                UUID.randomUUID(),
                new LicensePlate("ABC1234"),
                null,
                "Volkswagen",
                "Gol",
                VehicleYear.create(2026, 2026),
                "Prata",
                null,
                active);
    }
}
