package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception.VehicleNotFoundException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Mileage;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleArchivedException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateVehicleUseCaseTest {

    @Mock
    private VehicleRepository repository;

    private UpdateVehicleUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T12:00:00Z"), ZoneOffset.UTC);
        useCase = new UpdateVehicleUseCase(repository, clock);
    }

    @Test
    void updatesDescriptionsAndAddsCanonicalChassis() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, true, null);
        ChassisNumber chassis = new ChassisNumber("9BWZZZ377VT004251");
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        VehicleResponse response = useCase.execute(id, request(" 9bwzzz377vt004251 "));

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(repository).existsByChassisNumberAndIdNot(chassis, id);
        verify(repository).save(captor.capture());
        assertEquals("Fiat", captor.getValue().brand());
        assertEquals("Argo", response.model());
        assertEquals(2025, response.year());
        assertEquals("Branco", response.color());
        assertEquals(chassis.value(), response.chassis());
    }

    @Test
    void preservesExistingChassisForNullEmptyAndBlankInput() {
        String chassis = "9BWZZZ377VT004251";
        String[] preservedInputs = {null, "", "   "};

        for (String input : preservedInputs) {
            UUID id = UUID.randomUUID();
            when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle(id, true, chassis)));

            VehicleResponse response = useCase.execute(id, request(input));

            assertEquals(chassis, response.chassis());
        }

        verify(repository, times(3)).save(any(Vehicle.class));
        verify(repository, never()).existsByChassisNumberAndIdNot(any(), any());
    }

    @Test
    void treatsCurrentCanonicalChassisAsIdempotent() {
        UUID id = UUID.randomUUID();
        String chassis = "9BWZZZ377VT004251";
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle(id, true, chassis)));

        VehicleResponse response = useCase.execute(id, request(" 9bwzzz377vt004251 "));

        assertEquals(chassis, response.chassis());
        verify(repository, never()).existsByChassisNumberAndIdNot(any(), any());
        verify(repository).save(any(Vehicle.class));
    }

    @Test
    void preservesMileageWhileUpdatingDescriptiveData() {
        UUID id = UUID.randomUUID();
        Vehicle vehicle = vehicle(id, true, null);
        vehicle.recordMileage(new Mileage(42_500));
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.of(vehicle));

        VehicleResponse response = useCase.execute(id, request(null));

        assertEquals(42_500L, response.mileage());
        verify(repository).save(vehicle);
    }

    @Test
    void rejectsMissingAndArchivedVehicleWithoutSaving() {
        UUID missingId = UUID.randomUUID();
        when(repository.findByIdForUpdate(missingId)).thenReturn(Optional.empty());
        assertThrows(VehicleNotFoundException.class, () -> useCase.execute(missingId, request(null)));

        UUID archivedId = UUID.randomUUID();
        when(repository.findByIdForUpdate(archivedId))
                .thenReturn(Optional.of(vehicle(archivedId, false, "9BWZZZ377VT004251")));
        assertThrows(VehicleArchivedException.class,
                () -> useCase.execute(archivedId, request("9BWZZZ377VT004252")));

        verify(repository, never()).existsByChassisNumberAndIdNot(any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsChassisOwnedByAnotherVehicleWithoutSaving() {
        UUID id = UUID.randomUUID();
        ChassisNumber chassis = new ChassisNumber("9BWZZZ377VT004252");
        when(repository.findByIdForUpdate(id))
                .thenReturn(Optional.of(vehicle(id, true, "9BWZZZ377VT004251")));
        when(repository.existsByChassisNumberAndIdNot(chassis, id)).thenReturn(true);

        assertThrows(VehicleChassisAlreadyExistsException.class,
                () -> useCase.execute(id, request(chassis.value())));

        verify(repository, never()).save(any());
    }

    @Test
    void validatesYearAndNonBlankChassisBeforeRepositoryLookup() {
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(UUID.randomUUID(), requestWithYear(null, 2028)));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(UUID.randomUUID(), request("invalid")));

        verifyNoInteractions(repository);
    }

    private static UpdateVehicleRequest request(String chassis) {
        return requestWithYear(chassis, 2025);
    }

    private static UpdateVehicleRequest requestWithYear(String chassis, int year) {
        return new UpdateVehicleRequest(" Fiat ", " Argo ", year, " Branco ", chassis);
    }

    private static Vehicle vehicle(UUID id, boolean active, String chassis) {
        ChassisNumber chassisNumber = chassis == null ? null : new ChassisNumber(chassis);
        return Vehicle.reconstitute(id, UUID.randomUUID(), new LicensePlate("ABC1234"), chassisNumber,
                "Volkswagen", "Gol", VehicleYear.create(2026, 2026), "Prata", null, active);
    }
}
