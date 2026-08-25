package br.com.fiap.workshop_management_system.registration.vehicle.application.api;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class VehicleAvailabilityApiIntegrationTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(920_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(2000);

    @Autowired
    private VehicleAvailabilityApi availabilityApi;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void requiresAConsumerTransaction() {
        assertThrows(
                IllegalTransactionStateException.class,
                () -> availabilityApi.checkForNewWork(UUID.randomUUID()));
    }

    @Test
    void reportsEveryAvailabilityUnderTheConsumerTransaction() {
        Customer customer = persistCustomer();
        Vehicle active = vehicle(customer.id());
        Vehicle archived = vehicle(customer.id());
        archived.archive();
        vehicleRepository.save(active);
        vehicleRepository.save(archived);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        assertEquals(
                VehicleAvailability.ACTIVE,
                transaction.execute(status -> availabilityApi.checkForNewWork(active.id())));
        assertEquals(
                VehicleAvailability.ARCHIVED,
                transaction.execute(status -> availabilityApi.checkForNewWork(archived.id())));
        assertEquals(
                VehicleAvailability.NOT_FOUND,
                transaction.execute(status -> availabilityApi.checkForNewWork(UUID.randomUUID())));
    }

    private Customer persistCustomer() {
        Customer customer = Customer.create(
                "Cliente API Vehicle",
                new TaxId(nextValidCpf()),
                new ContactInfo("vehicle-api@example.test", "+5511999999999"));
        customerRepository.save(customer);
        return customer;
    }

    private static Vehicle vehicle(UUID customerId) {
        return Vehicle.create(
                customerId,
                new LicensePlate("API%04d".formatted(PLATE_SEQUENCE.getAndIncrement())),
                null,
                "Volkswagen",
                "Gol",
                VehicleYear.create(2026, 2026),
                "Prata",
                null);
    }

    private static String nextValidCpf() {
        String base = "%09d".formatted(CPF_SEQUENCE.getAndIncrement());
        int firstCheckDigit = calculateCpfCheckDigit(base);
        String partialCpf = base + firstCheckDigit;
        return partialCpf + calculateCpfCheckDigit(partialCpf);
    }

    private static int calculateCpfCheckDigit(String digits) {
        int sum = 0;
        for (int index = 0; index < digits.length(); index++) {
            sum += (digits.charAt(index) - '0') * (digits.length() + 1 - index);
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
