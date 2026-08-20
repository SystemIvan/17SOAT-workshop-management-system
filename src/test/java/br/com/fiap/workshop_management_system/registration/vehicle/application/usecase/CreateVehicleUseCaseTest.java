package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.CustomerArchivedException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.CreateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateVehicleUseCaseTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private CreateVehicleUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-17T12:00:00Z"), ZoneOffset.UTC);
        useCase = new CreateVehicleUseCase(customerRepository, vehicleRepository, clock);
    }

    @Test
    void createsVehicleForActiveCustomerWithoutChassis() {
        UUID customerId = UUID.randomUUID();
        Customer customer = customer();
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));

        VehicleResponse response = useCase.execute(request(customerId, "ABC-1234", null));

        ArgumentCaptor<Vehicle> captor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(captor.capture());
        Vehicle saved = captor.getValue();
        assertEquals(saved.id(), response.id());
        assertEquals(customerId, response.customerId());
        assertEquals("ABC1234", response.licensePlate());
        assertNull(response.chassis());
        assertTrue(response.active());
        assertTrue(customer.active());
        verify(vehicleRepository, never()).existsByChassisNumber(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void normalizesAndChecksOptionalChassis() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer()));

        VehicleResponse response = useCase.execute(request(customerId, "abc1d23", " 9bwzzz377vt004251 "));

        assertEquals("ABC1D23", response.licensePlate());
        assertEquals("9BWZZZ377VT004251", response.chassis());
        verify(vehicleRepository).existsByChassisNumber(new ChassisNumber("9BWZZZ377VT004251"));
    }

    @Test
    void rejectsMissingAndArchivedCustomerWithoutSaving() {
        UUID missingCustomerId = UUID.randomUUID();
        when(customerRepository.findByIdForUpdate(missingCustomerId)).thenReturn(Optional.empty());
        assertThrows(CustomerNotFoundException.class,
                () -> useCase.execute(request(missingCustomerId, "ABC1234", null)));

        UUID archivedCustomerId = UUID.randomUUID();
        Customer archivedCustomer = customer();
        archivedCustomer.archive();
        when(customerRepository.findByIdForUpdate(archivedCustomerId)).thenReturn(Optional.of(archivedCustomer));
        assertThrows(CustomerArchivedException.class,
                () -> useCase.execute(request(archivedCustomerId, "DEF1234", null)));

        verifyNoInteractions(vehicleRepository);
    }

    @Test
    void rejectsDuplicatePlateBeforeCheckingChassis() {
        UUID customerId = UUID.randomUUID();
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer()));
        when(vehicleRepository.existsByLicensePlate(new LicensePlate("ABC1234"))).thenReturn(true);

        assertThrows(VehicleLicensePlateAlreadyExistsException.class,
                () -> useCase.execute(request(customerId, "ABC1234", "9BWZZZ377VT004251")));

        verify(vehicleRepository, never()).existsByChassisNumber(org.mockito.ArgumentMatchers.any());
        verify(vehicleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateChassis() {
        UUID customerId = UUID.randomUUID();
        ChassisNumber chassisNumber = new ChassisNumber("9BWZZZ377VT004251");
        when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer()));
        when(vehicleRepository.existsByChassisNumber(chassisNumber)).thenReturn(true);

        assertThrows(VehicleChassisAlreadyExistsException.class,
                () -> useCase.execute(request(customerId, "ABC1234", chassisNumber.value())));

        verify(vehicleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validatesVehicleBeforeConsultingRepositories() {
        CreateVehicleRequest invalidRequest = new CreateVehicleRequest(UUID.randomUUID(), "invalid", null,
                "Brand", "Model", 2026, "Color");

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(invalidRequest));

        verifyNoInteractions(customerRepository, vehicleRepository);
    }

    private static CreateVehicleRequest request(UUID customerId, String plate, String chassis) {
        return new CreateVehicleRequest(customerId, plate, chassis, "Volkswagen", "Gol", 2026, "Prata");
    }

    private static Customer customer() {
        return Customer.create("Cliente", new TaxId("52998224725"),
                new ContactInfo("customer@example.test", "+5511999999999"));
    }
}
