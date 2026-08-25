package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveVehicleUseCaseTest {

    @Mock
    private VehicleRepository repository;

    private ArchiveVehicleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ArchiveVehicleUseCase(repository);
    }

    @Test
    void archivesActiveVehicleAndSavesTheChange() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, true);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        useCase.execute(id);

        assertFalse(vehicle.active());
        verify(repository).save(vehicle);
    }

    @Test
    void doesNotSaveRepeatedArchive() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, false);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        useCase.execute(id);

        verify(repository, never()).save(vehicle);
    }

    @Test
    void rejectsMissingVehicleWithoutSaving() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThrows(VehicleNotFoundException.class, () -> useCase.execute(id));

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
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
