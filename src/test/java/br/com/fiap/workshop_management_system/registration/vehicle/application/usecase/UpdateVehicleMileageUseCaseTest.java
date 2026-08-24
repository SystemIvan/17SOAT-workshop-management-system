package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleMileageRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception.VehicleNotFoundException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Mileage;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleArchivedException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleMileageCannotDecreaseException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateVehicleMileageUseCaseTest {

    @Mock
    private VehicleRepository repository;

    private UpdateVehicleMileageUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateVehicleMileageUseCase(repository);
    }

    @Test
    void recordsFirstMileageAndPersistsChange() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, null, true);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        VehicleResponse response = useCase.execute(id, new UpdateVehicleMileageRequest(42_500L));

        assertEquals(42_500L, response.mileage());
        verify(repository).save(vehicle);
    }

    @Test
    void recordsGreaterMileageAndPreservesOtherVehicleData() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, 42_500L, true);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        VehicleResponse response = useCase.execute(id, new UpdateVehicleMileageRequest(43_000L));

        assertEquals(43_000L, response.mileage());
        assertEquals("Volkswagen", response.brand());
        assertEquals("Gol", response.model());
        assertEquals("ABC1234", response.licensePlate());
        verify(repository).save(vehicle);
    }

    @Test
    void returnsCurrentVehicleWithoutSavingWhenMileageIsEqual() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, 42_500L, true);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        VehicleResponse response = useCase.execute(id, new UpdateVehicleMileageRequest(42_500L));

        assertEquals(42_500L, response.mileage());
        verify(repository, never()).save(vehicle);
    }

    @Test
    void rejectsDecreaseWithoutSaving() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, 42_500L, true);
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        assertThrows(VehicleMileageCannotDecreaseException.class,
                () -> useCase.execute(id, new UpdateVehicleMileageRequest(42_499L)));

        assertEquals(42_500, vehicle.mileage().orElseThrow().value());
        verify(repository, never()).save(vehicle);
    }

    @Test
    void rejectsMissingAndArchivedVehicleWithoutSaving() {
        UUID missingId = UUID.randomUUID();
        when(repository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());
        assertThrows(VehicleNotFoundException.class,
                () -> useCase.execute(missingId, new UpdateVehicleMileageRequest(1L)));

        UUID archivedId = UUID.randomUUID();
        Vehicle archived = vehicle(archivedId, null, false);
        when(repository.findByIdForUpdate(archivedId)).thenReturn(Optional.of(archived));
        assertThrows(VehicleArchivedException.class,
                () -> useCase.execute(archivedId, new UpdateVehicleMileageRequest(1L)));

        verify(repository, never()).save(archived);
    }

    @Test
    void validatesRequestBeforeRepositoryLookup() {
        assertThrows(NullPointerException.class, () -> useCase.execute(UUID.randomUUID(), null));
        assertThrows(NullPointerException.class,
                () -> useCase.execute(UUID.randomUUID(), new UpdateVehicleMileageRequest(null)));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(UUID.randomUUID(), new UpdateVehicleMileageRequest(-1L)));

        verifyNoInteractions(repository);
    }

    private static Vehicle vehicle(UUID id, Long mileage, boolean active) {
        return Vehicle.reconstitute(
                id,
                UUID.randomUUID(),
                new LicensePlate("ABC1234"),
                null,
                "Volkswagen",
                "Gol",
                VehicleYear.create(2026, 2026),
                "Prata",
                mileage == null ? null : new Mileage(mileage),
                active);
    }
}
