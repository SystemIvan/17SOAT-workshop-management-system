package br.com.fiap.workshop_management_system.registration.vehicle.application.api;

import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeApi;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeCommand;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeOutcome;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeResult;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class RegistrationIntakeApiIntegrationTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(960_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger(3000);

    @Autowired
    private CustomerIntakeApi customerIntakeApi;

    @Autowired
    private VehicleIntakeApi vehicleIntakeApi;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void requiresAConsumerTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> customerIntakeApi.identifyOrRegister(customerCommand(nextValidCpf())));
        assertThrows(IllegalTransactionStateException.class,
                () -> vehicleIntakeApi.identifyOrRegister(vehicleCommand(UUID.randomUUID(), nextPlate())));
    }

    @Test
    void registersThenReusesCustomerAndVehicleAcrossTransactions() {
        String document = nextValidCpf();
        String plate = nextPlate();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        CustomerIntakeResult firstCustomer = transaction.execute(
                status -> customerIntakeApi.identifyOrRegister(customerCommand(document)));
        VehicleIntakeResult firstVehicle = transaction.execute(
                status -> vehicleIntakeApi.identifyOrRegister(vehicleCommand(firstCustomer.customerId(), plate)));
        CustomerIntakeResult secondCustomer = transaction.execute(
                status -> customerIntakeApi.identifyOrRegister(customerCommand(document)));
        VehicleIntakeResult secondVehicle = transaction.execute(
                status -> vehicleIntakeApi.identifyOrRegister(vehicleCommand(firstCustomer.customerId(), plate)));

        assertEquals(CustomerIntakeOutcome.CREATED, firstCustomer.outcome());
        assertEquals(VehicleIntakeOutcome.CREATED, firstVehicle.outcome());
        assertEquals(CustomerIntakeOutcome.REUSED, secondCustomer.outcome());
        assertEquals(firstCustomer.customerId(), secondCustomer.customerId());
        assertEquals(VehicleIntakeOutcome.REUSED, secondVehicle.outcome());
        assertEquals(firstVehicle.vehicleId(), secondVehicle.vehicleId());
        assertEquals(plate, secondVehicle.licensePlate());
    }

    @Test
    void reportsPlateOwnedByAnotherCustomer() {
        String plate = nextPlate();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID owner = transaction.execute(
                status -> customerIntakeApi.identifyOrRegister(customerCommand(nextValidCpf()))).customerId();
        UUID other = transaction.execute(
                status -> customerIntakeApi.identifyOrRegister(customerCommand(nextValidCpf()))).customerId();
        transaction.executeWithoutResult(status -> vehicleIntakeApi.identifyOrRegister(vehicleCommand(owner, plate)));

        VehicleIntakeResult result = transaction.execute(
                status -> vehicleIntakeApi.identifyOrRegister(vehicleCommand(other, plate)));

        assertEquals(VehicleIntakeOutcome.OWNED_BY_ANOTHER_CUSTOMER, result.outcome());
    }

    @Test
    void consumerRollbackDiscardsCustomerAndVehicleRegisteredInTheSameTransaction() {
        String document = nextValidCpf();
        String plate = nextPlate();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            UUID customerId = customerIntakeApi.identifyOrRegister(customerCommand(document)).customerId();
            vehicleIntakeApi.identifyOrRegister(vehicleCommand(customerId, plate));
            assertTrue(vehicleRepository.findByLicensePlateForUpdate(new LicensePlate(plate)).isPresent());
            status.setRollbackOnly();
        });

        assertFalse(customerRepository.existsByTaxId(new TaxId(document)));
        assertFalse(vehicleRepository.existsByLicensePlate(new LicensePlate(plate)));
    }

    private static CustomerIntakeCommand customerCommand(String document) {
        return new CustomerIntakeCommand(document, "Cliente Intake", "intake@example.test", "+5511999999999", null);
    }

    private static VehicleIntakeCommand vehicleCommand(UUID customerId, String plate) {
        return new VehicleIntakeCommand(customerId, plate, null, "Fiat", "Argo", 2022, "Prata", null);
    }

    private static String nextPlate() {
        return "INT%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
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
