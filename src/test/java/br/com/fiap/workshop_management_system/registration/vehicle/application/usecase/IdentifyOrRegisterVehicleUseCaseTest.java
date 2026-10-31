package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeCommand;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeOutcome;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleIntakeResult;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentifyOrRegisterVehicleUseCaseTest {

    private static final LicensePlate PLATE = new LicensePlate("ABC1D23");

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private IdentifyOrRegisterVehicleUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
        useCase = new IdentifyOrRegisterVehicleUseCase(customerRepository, vehicleRepository, clock);
    }

    @Test
    void reusesActiveVehicleOfTheSameCustomerWithoutChangingIt() {
        UUID customerId = UUID.randomUUID();
        Vehicle existing = vehicle(customerId);
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.of(existing));

        VehicleIntakeResult result = useCase.identifyOrRegister(
                new VehicleIntakeCommand(customerId, "abc1d23", null, "Outra", "Outro", 2000, "Azul", null));

        assertEquals(VehicleIntakeOutcome.REUSED, result.outcome());
        assertEquals(existing.id(), result.vehicleId());
        assertEquals("ABC1D23", result.licensePlate());
        assertEquals("Fiat", result.brand());
        assertEquals("Argo", result.model());
        assertEquals(2022, result.year());
        verify(vehicleRepository, never()).save(any());
        verifyNoInteractions(customerRepository);
    }

    @Test
    void rejectsActiveVehicleOwnedByAnotherCustomer() {
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE))
                .thenReturn(Optional.of(vehicle(UUID.randomUUID())));

        VehicleIntakeResult result = useCase.identifyOrRegister(command(UUID.randomUUID(), null));

        assertEquals(VehicleIntakeOutcome.OWNED_BY_ANOTHER_CUSTOMER, result.outcome());
        assertNull(result.vehicleId());
        assertNull(result.licensePlate());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void rejectsArchivedVehicleEvenForTheSameCustomer() {
        UUID customerId = UUID.randomUUID();
        Vehicle archived = vehicle(customerId);
        archived.archive();
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.of(archived));

        VehicleIntakeResult result = useCase.identifyOrRegister(command(customerId, null));

        assertEquals(VehicleIntakeOutcome.ARCHIVED, result.outcome());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void registersNewVehicleForActiveCustomer() {
        UUID customerId = UUID.randomUUID();
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.empty());
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer()));

        VehicleIntakeResult result = useCase.identifyOrRegister(command(customerId, null));

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        Vehicle saved = captor.getValue();
        assertEquals(VehicleIntakeOutcome.CREATED, result.outcome());
        assertEquals(saved.id(), result.vehicleId());
        assertEquals(customerId, saved.customerId());
        assertEquals("ABC1D23", result.licensePlate());
        assertEquals("Fiat", result.brand());
        assertEquals(2022, result.year());
        verify(vehicleRepository, never()).existsByChassisNumber(any());
    }

    @Test
    void rejectsNewVehicleWhenChassisIsAlreadyRegistered() {
        UUID customerId = UUID.randomUUID();
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.empty());
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer()));
        when(vehicleRepository.existsByChassisNumber(new ChassisNumber("9BWZZZ377VT004251"))).thenReturn(true);

        VehicleIntakeResult result = useCase.identifyOrRegister(command(customerId, "9BWZZZ377VT004251"));

        assertEquals(VehicleIntakeOutcome.CHASSIS_ALREADY_REGISTERED, result.outcome());
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void rejectsNewVehicleWithoutYear() {
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> useCase.identifyOrRegister(
                new VehicleIntakeCommand(UUID.randomUUID(), "ABC1D23", null, "Fiat", "Argo", null, "Prata", null)));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void rejectsNewVehicleWithoutBrand() {
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> useCase.identifyOrRegister(
                new VehicleIntakeCommand(UUID.randomUUID(), "ABC1D23", null, null, "Argo", 2022, "Prata", null)));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidPlateBeforeAnyLookup() {
        assertThrows(IllegalArgumentException.class, () -> useCase.identifyOrRegister(
                new VehicleIntakeCommand(UUID.randomUUID(), "12-ABC", null, "Fiat", "Argo", 2022, "Prata", null)));
        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void refusesToRegisterForInactiveCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer archived = customer();
        archived.archive();
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.empty());
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(archived));

        assertThrows(IllegalStateException.class, () -> useCase.identifyOrRegister(command(customerId, null)));
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    void translatesConcurrentRegistrationIntoIntakeConflict() {
        UUID customerId = UUID.randomUUID();
        when(vehicleRepository.findByLicensePlateForUpdate(PLATE)).thenReturn(Optional.empty());
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer()));
        doThrow(new VehicleLicensePlateAlreadyExistsException()).when(vehicleRepository).save(any());

        assertThrows(VehicleIntakeConflictException.class,
                () -> useCase.identifyOrRegister(command(customerId, null)));
    }

    private static VehicleIntakeCommand command(UUID customerId, String chassis) {
        return new VehicleIntakeCommand(customerId, "ABC1D23", chassis, "Fiat", "Argo", 2022, "Prata", 42_500L);
    }

    private static Vehicle vehicle(UUID customerId) {
        return Vehicle.create(customerId, PLATE, null, "Fiat", "Argo", VehicleYear.create(2022, 2026), "Prata", null);
    }

    private static Customer customer() {
        return Customer.create("Cliente", new TaxId("529.982.247-25"),
                new ContactInfo("cliente@example.test", "+5511999999999"));
    }
}
