package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class VehicleRepositoryIntegrationTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(500_000_000);

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleJpaRepository jpaRepository;

    @Autowired
    private VehiclePersistenceMapper mapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsAndRestoresVehicleWithOptionalChassis() {
        Customer customer = persistCustomer();
        Vehicle withoutChassis = vehicle(customer.id(), "PST1A01", null);
        Vehicle withChassis = vehicle(customer.id(), "PST1A02", "9BWZZZ377VT004251");

        vehicleRepository.save(withoutChassis);
        vehicleRepository.save(withChassis);

        VehicleJpaEntity storedWithoutChassis = jpaRepository.findById(withoutChassis.id()).orElseThrow();
        assertEquals(customer.id(), storedWithoutChassis.getCustomerId());
        assertEquals("PST1A01", storedWithoutChassis.getLicensePlate());
        assertNull(storedWithoutChassis.getChassisNumber());
        assertTrue(storedWithoutChassis.isActive());

        Vehicle restored = mapper.toDomain(jpaRepository.findById(withChassis.id()).orElseThrow());
        assertEquals(withChassis.id(), restored.id());
        assertEquals("9BWZZZ377VT004251", restored.chassisNumber().orElseThrow().value());
    }

    @Test
    void permitsMultipleNullChassisValues() {
        Customer customer = persistCustomer();

        vehicleRepository.save(vehicle(customer.id(), "NUL1A01", null));
        vehicleRepository.save(vehicle(customer.id(), "NUL1A02", null));

        assertTrue(vehicleRepository.existsByLicensePlate(new LicensePlate("NUL1A01")));
        assertTrue(vehicleRepository.existsByLicensePlate(new LicensePlate("NUL1A02")));
    }

    @Test
    void translatesDatabaseIdentityConflicts() {
        Customer customer = persistCustomer();
        vehicleRepository.save(vehicle(customer.id(), "DUP1A01", "9BWZZZ377VT004252"));

        assertThrows(VehicleLicensePlateAlreadyExistsException.class,
                () -> vehicleRepository.save(vehicle(customer.id(), "DUP1A01", null)));
        assertThrows(VehicleChassisAlreadyExistsException.class,
                () -> vehicleRepository.save(vehicle(customer.id(), "DUP1A02", "9BWZZZ377VT004252")));
    }

    @Test
    void databaseRejectsUnknownCustomerReference() {
        Vehicle vehicle = vehicle(UUID.randomUUID(), "FKT1A01", null);

        assertThrows(DataIntegrityViolationException.class, () -> vehicleRepository.save(vehicle));
    }

    @Test
    @Transactional
    void locksAndUpdatesVehicleWhileExcludingItsOwnChassisFromConflicts() {
        Customer customer = persistCustomer();
        String originalChassis = "9BWZZZ377VT004253";
        String replacementChassis = "9BWZZZ377VT004254";
        Vehicle original = vehicle(customer.id(), "UPD1A01", originalChassis);
        vehicleRepository.save(original);

        Vehicle locked = vehicleRepository.findByIdForUpdate(original.id()).orElseThrow();
        assertFalse(vehicleRepository.existsByChassisNumberAndIdNot(
                new ChassisNumber(originalChassis), original.id()));

        locked.updateDetails(" Fiat ", " Argo ", VehicleYear.create(2025, 2026), " Branco ",
                new ChassisNumber(replacementChassis));
        vehicleRepository.save(locked);

        VehicleJpaEntity stored = jpaRepository.findById(original.id()).orElseThrow();
        assertEquals("Fiat", stored.getBrand());
        assertEquals("Argo", stored.getModel());
        assertEquals(2025, stored.getModelYear());
        assertEquals("Branco", stored.getColor());
        assertEquals(replacementChassis, stored.getChassisNumber());
        assertFalse(vehicleRepository.existsByChassisNumber(new ChassisNumber(originalChassis)));
    }

    @Test
    void detectsChassisOwnedByAnotherVehicle() {
        Customer customer = persistCustomer();
        String chassis = "9BWZZZ377VT004255";
        Vehicle owner = vehicle(customer.id(), "UPD1A02", chassis);
        Vehicle candidate = vehicle(customer.id(), "UPD1A03", null);
        vehicleRepository.save(owner);
        vehicleRepository.save(candidate);

        assertTrue(vehicleRepository.existsByChassisNumberAndIdNot(new ChassisNumber(chassis), candidate.id()));
    }

    @Test
    void serializesConcurrentUpdatesForTheSameVehicle() throws Exception {
        Customer customer = persistCustomer();
        Vehicle original = vehicle(customer.id(), "LCK1A01", null);
        vehicleRepository.save(original);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstUpdate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstUpdate = executor.submit(() -> inTransaction(() -> {
                Vehicle locked = vehicleRepository.findByIdForUpdate(original.id()).orElseThrow();
                firstLockAcquired.countDown();
                await(releaseFirstUpdate);
                locked.updateDetails("Fiat", "Argo", VehicleYear.create(2025, 2026), "Branco", null);
                vehicleRepository.save(locked);
            }));
            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS));

            Future<?> secondUpdate = executor.submit(() -> inTransaction(() -> {
                Vehicle locked = vehicleRepository.findByIdForUpdate(original.id()).orElseThrow();
                locked.updateDetails("Honda", "City", VehicleYear.create(2024, 2026), "Cinza", null);
                vehicleRepository.save(locked);
            }));

            assertThrows(TimeoutException.class, () -> secondUpdate.get(250, TimeUnit.MILLISECONDS));
            releaseFirstUpdate.countDown();
            firstUpdate.get(5, TimeUnit.SECONDS);
            secondUpdate.get(5, TimeUnit.SECONDS);

            VehicleJpaEntity stored = jpaRepository.findById(original.id()).orElseThrow();
            assertEquals("Honda", stored.getBrand());
            assertEquals("City", stored.getModel());
        } finally {
            releaseFirstUpdate.countDown();
            executor.shutdownNow();
        }
    }

    private Customer persistCustomer() {
        Customer customer = Customer.create("Cliente Vehicle", new TaxId(nextValidCpf()),
                new ContactInfo("vehicle@example.test", "+5511999999999"));
        customerRepository.save(customer);
        return customer;
    }

    private static Vehicle vehicle(UUID customerId, String licensePlate, String chassis) {
        ChassisNumber chassisNumber = chassis == null ? null : new ChassisNumber(chassis);
        return Vehicle.create(customerId, new LicensePlate(licensePlate), chassisNumber,
                "Volkswagen", "Gol", VehicleYear.create(2026, 2026), "Prata");
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Tempo excedido aguardando liberação do update concorrente");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Update concorrente interrompido", exception);
        }
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
