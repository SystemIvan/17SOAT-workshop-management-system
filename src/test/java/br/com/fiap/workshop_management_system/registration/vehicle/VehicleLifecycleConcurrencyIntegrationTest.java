package br.com.fiap.workshop_management_system.registration.vehicle;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailability;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailabilityApi;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleMileageRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.ArchiveVehicleUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.UpdateVehicleMileageUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.application.usecase.UpdateVehicleUseCase;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleArchivedException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.CreateServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.VehicleSnapshotRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderVehicleArchivedException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .CreateServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class VehicleLifecycleConcurrencyIntegrationTest {

    private static final AtomicInteger CPF_SEQUENCE = new AtomicInteger(910_000_000);
    private static final AtomicInteger PLATE_SEQUENCE = new AtomicInteger();

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private ArchiveVehicleUseCase archiveVehicleUseCase;

    @Autowired
    private UpdateVehicleUseCase updateVehicleUseCase;

    @Autowired
    private UpdateVehicleMileageUseCase updateVehicleMileageUseCase;

    @Autowired
    private CreateServiceOrderUseCase createServiceOrderUseCase;

    @Autowired
    private VehicleAvailabilityApi vehicleAvailabilityApi;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void archiveHoldingTheLockMakesConcurrentServiceOrderCreationWaitAndThenRejectsIt() throws Exception {
        Vehicle vehicle = persistVehicle();
        CountDownLatch archiveReturnedInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseArchiveCommit = new CountDownLatch(1);
        CountDownLatch creationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> archive = executor.submit(() -> inTransaction(() -> {
                archiveVehicleUseCase.execute(vehicle.id());
                archiveReturnedInsideTransaction.countDown();
                await(releaseArchiveCommit);
                return null;
            }));
            assertTrue(archiveReturnedInsideTransaction.await(5, TimeUnit.SECONDS));

            Future<ServiceOrderResponse> creation = executor.submit(() -> {
                creationStarted.countDown();
                return createServiceOrderUseCase.execute(requestFor(vehicle));
            });
            assertTrue(creationStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> creation.get(250, TimeUnit.MILLISECONDS));

            releaseArchiveCommit.countDown();
            archive.get(5, TimeUnit.SECONDS);
            ExecutionException exception = assertThrows(
                    ExecutionException.class, () -> creation.get(5, TimeUnit.SECONDS));

            assertInstanceOf(ServiceOrderVehicleArchivedException.class, exception.getCause());
            assertFalse(vehicleRepository.findById(vehicle.id()).orElseThrow().active());
        } finally {
            releaseArchiveCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void serviceOrderHoldingTheLockCommitsBeforeArchiveAndRemainsUnchanged() throws Exception {
        Vehicle vehicle = persistVehicle();
        CountDownLatch creationReturnedInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseCreationCommit = new CountDownLatch(1);
        CountDownLatch archiveStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<ServiceOrderResponse> creation = executor.submit(() -> inTransaction(() -> {
                ServiceOrderResponse response = createServiceOrderUseCase.execute(requestFor(vehicle));
                creationReturnedInsideTransaction.countDown();
                await(releaseCreationCommit);
                return response;
            }));
            assertTrue(creationReturnedInsideTransaction.await(5, TimeUnit.SECONDS));

            Future<?> archive = executor.submit(() -> {
                archiveStarted.countDown();
                archiveVehicleUseCase.execute(vehicle.id());
            });
            assertTrue(archiveStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> archive.get(250, TimeUnit.MILLISECONDS));

            releaseCreationCommit.countDown();
            ServiceOrderResponse created = creation.get(5, TimeUnit.SECONDS);
            archive.get(5, TimeUnit.SECONDS);

            ServiceOrderResponse persisted = inTransaction(() -> ServiceOrderMapper.toResponse(
                    serviceOrderRepository.findById(created.id()).orElseThrow()));
            assertEquals(created, persisted);
            assertFalse(vehicleRepository.findById(vehicle.id()).orElseThrow().active());
        } finally {
            releaseCreationCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void aServiceOrderStartedAfterArchiveCommitIsNotPersisted() throws Exception {
        Vehicle vehicle = persistVehicle();

        archiveVehicleUseCase.execute(vehicle.id());

        assertThrows(ServiceOrderVehicleArchivedException.class,
                () -> createServiceOrderUseCase.execute(requestFor(vehicle)));
    }

    @Test
    void archiveSerializesWithDetailAndMileageUpdatesUnderTheSameLifecycle() throws Exception {
        Vehicle vehicle = persistVehicle();
        CountDownLatch archiveReturnedInsideTransaction = new CountDownLatch(1);
        CountDownLatch releaseArchiveCommit = new CountDownLatch(1);
        CountDownLatch updatesStarted = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        try {
            Future<?> archive = executor.submit(() -> inTransaction(() -> {
                archiveVehicleUseCase.execute(vehicle.id());
                archiveReturnedInsideTransaction.countDown();
                await(releaseArchiveCommit);
                return null;
            }));
            assertTrue(archiveReturnedInsideTransaction.await(5, TimeUnit.SECONDS));

            Future<?> details = executor.submit(() -> {
                updatesStarted.countDown();
                updateVehicleUseCase.execute(vehicle.id(),
                        new UpdateVehicleRequest("Fiat", "Argo", 2025, "Branco", null));
            });
            Future<?> mileage = executor.submit(() -> {
                updatesStarted.countDown();
                updateVehicleMileageUseCase.execute(vehicle.id(), new UpdateVehicleMileageRequest(10_000L));
            });
            assertTrue(updatesStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> details.get(250, TimeUnit.MILLISECONDS));
            assertThrows(TimeoutException.class, () -> mileage.get(250, TimeUnit.MILLISECONDS));

            releaseArchiveCommit.countDown();
            archive.get(5, TimeUnit.SECONDS);
            assertFutureFailedWith(details, VehicleArchivedException.class);
            assertFutureFailedWith(mileage, VehicleArchivedException.class);

            Vehicle persisted = vehicleRepository.findById(vehicle.id()).orElseThrow();
            assertFalse(persisted.active());
            assertEquals("Volkswagen", persisted.brand());
            assertTrue(persisted.mileage().isEmpty());
        } finally {
            releaseArchiveCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lockingOneVehicleDoesNotBlockEligibilityCheckForAnotherVehicle() throws Exception {
        Vehicle first = persistVehicle();
        Vehicle second = persistVehicle();
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<VehicleAvailability> firstCheck = executor.submit(() -> inTransaction(() -> {
                VehicleAvailability availability = vehicleAvailabilityApi.checkForNewWork(first.id());
                firstLockAcquired.countDown();
                await(releaseFirstLock);
                return availability;
            }));
            assertTrue(firstLockAcquired.await(5, TimeUnit.SECONDS));

            Future<VehicleAvailability> secondCheck = executor.submit(
                    () -> inTransaction(() -> vehicleAvailabilityApi.checkForNewWork(second.id())));

            assertEquals(VehicleAvailability.ACTIVE, secondCheck.get(5, TimeUnit.SECONDS));
            releaseFirstLock.countDown();
            assertEquals(VehicleAvailability.ACTIVE, firstCheck.get(5, TimeUnit.SECONDS));
        } finally {
            releaseFirstLock.countDown();
            executor.shutdownNow();
        }
    }

    private Vehicle persistVehicle() {
        Customer customer = Customer.create("Cliente concorrente", new TaxId(nextValidCpf()),
                new ContactInfo("concurrency@example.test", "+5511999999999"));
        customerRepository.save(customer);
        Vehicle vehicle = Vehicle.create(customer.id(), new LicensePlate(nextPlate()), null,
                "Volkswagen", "Gol", VehicleYear.create(2026, 2026), "Prata", null);
        vehicleRepository.save(vehicle);
        return vehicle;
    }

    private static CreateServiceOrderRequest requestFor(Vehicle vehicle) {
        return new CreateServiceOrderRequest(
                vehicle.customerId(),
                vehicle.id(),
                new VehicleSnapshotRequest(vehicle.licensePlate().value(), vehicle.brand(), vehicle.model(),
                        vehicle.year().value()),
                null,
                "Initial assessment");
    }

    private <T> T inTransaction(java.util.concurrent.Callable<T> action) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return action.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private static void assertFutureFailedWith(Future<?> future, Class<? extends Throwable> expected) {
        ExecutionException exception = assertThrows(
                ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(expected, exception.getCause());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Tempo excedido aguardando a operação concorrente");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Operação concorrente interrompida", exception);
        }
    }

    private static String nextPlate() {
        return "CCY%04d".formatted(PLATE_SEQUENCE.getAndIncrement());
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
