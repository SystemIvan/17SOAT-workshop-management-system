package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CatalogServiceNameConcurrencyIntegrationTest {

    @Autowired
    private CatalogServiceRepository repository;

    @Autowired
    private CatalogServiceJpaRepository jpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void permitsExactlyOneConcurrentInsertAndReportsTheWinner() throws Exception {
        String displayName = "Revisão Concorrente " + UUID.randomUUID();
        CatalogService first = service(displayName);
        CatalogService second = service(displayName.toUpperCase(Locale.ROOT));
        CyclicBarrier preCheckBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Attempt> firstAttempt = executor.submit(() -> attempt(first, preCheckBarrier));
            Future<Attempt> secondAttempt = executor.submit(() -> attempt(second, preCheckBarrier));

            List<Attempt> attempts = List.of(
                    firstAttempt.get(10, TimeUnit.SECONDS),
                    secondAttempt.get(10, TimeUnit.SECONDS));
            Attempt success = attempts.stream().filter(Attempt::successful).findFirst().orElseThrow();
            Attempt conflict = attempts.stream().filter(attempt -> !attempt.successful()).findFirst().orElseThrow();

            assertEquals(1, attempts.stream().filter(Attempt::successful).count());
            assertEquals(1, attempts.stream().filter(attempt -> !attempt.successful()).count());
            CatalogServiceNameAlreadyExistsException exception = assertInstanceOf(
                    CatalogServiceNameAlreadyExistsException.class, conflict.failure());
            assertEquals(success.catalogService().id(), exception.existingId());
            assertEquals(success.catalogService().name().value(), exception.existingName());
            assertEquals("Já existe um serviço cadastrado com esse nome: "
                    + success.catalogService().id() + " - " + success.catalogService().name().value(),
                    exception.getMessage());
            assertEquals(1, jpaRepository.findAll().stream()
                    .filter(entity -> entity.getId().equals(first.id()) || entity.getId().equals(second.id()))
                    .count());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void serializesConcurrentRenameAndPriceUpdateForTheSameService() throws Exception {
        CatalogService original = service("Serviço bloqueado " + UUID.randomUUID());
        repository.save(original);
        CountDownLatch renameLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseRename = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> rename = executor.submit(() -> inTransaction(() -> {
                CatalogService locked = repository.findByIdForUpdate(original.id()).orElseThrow();
                renameLockAcquired.countDown();
                await(releaseRename);
                locked.rename(new CatalogServiceName("Serviço renomeado " + original.id()));
                repository.save(locked);
            }));
            assertTrue(renameLockAcquired.await(5, TimeUnit.SECONDS));

            Future<?> priceUpdate = executor.submit(() -> inTransaction(() -> {
                CatalogService locked = repository.findByIdForUpdate(original.id()).orElseThrow();
                locked.updateBasePrice(new Money(new BigDecimal("199.90"), CurrencyCode.BRL));
                repository.save(locked);
            }));

            assertThrows(TimeoutException.class, () -> priceUpdate.get(250, TimeUnit.MILLISECONDS));
            releaseRename.countDown();
            rename.get(5, TimeUnit.SECONDS);
            priceUpdate.get(5, TimeUnit.SECONDS);

            CatalogService stored = repository.findById(original.id()).orElseThrow();
            assertEquals("Serviço renomeado " + original.id(), stored.name().value());
            assertEquals(new BigDecimal("199.90"), stored.basePrice().value());
            assertTrue(stored.active());
        } finally {
            releaseRename.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void permitsExactlyOneConcurrentRenameToTheSameCanonicalName() throws Exception {
        CatalogService first = service("Primeiro " + UUID.randomUUID());
        CatalogService second = service("Segundo " + UUID.randomUUID());
        repository.save(first);
        repository.save(second);
        String desiredName = "Nome disputado " + UUID.randomUUID();
        CyclicBarrier preCheckBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<RenameAttempt> firstAttempt = executor.submit(
                    () -> attemptRename(first.id(), desiredName, preCheckBarrier));
            Future<RenameAttempt> secondAttempt = executor.submit(
                    () -> attemptRename(second.id(), desiredName.toUpperCase(Locale.ROOT), preCheckBarrier));
            List<RenameAttempt> attempts = List.of(
                    firstAttempt.get(10, TimeUnit.SECONDS),
                    secondAttempt.get(10, TimeUnit.SECONDS));
            RenameAttempt success = attempts.stream().filter(RenameAttempt::successful).findFirst().orElseThrow();
            RenameAttempt conflict = attempts.stream()
                    .filter(attempt -> !attempt.successful())
                    .findFirst()
                    .orElseThrow();

            assertEquals(1, attempts.stream().filter(RenameAttempt::successful).count());
            CatalogServiceNameAlreadyExistsException exception = assertInstanceOf(
                    CatalogServiceNameAlreadyExistsException.class, conflict.failure());
            CatalogService winner = repository.findById(success.catalogServiceId()).orElseThrow();
            assertEquals(winner.id(), exception.existingId());
            assertEquals(winner.name().value(), exception.existingName());
            assertEquals(winner.id(), repository.findByName(new CatalogServiceName(desiredName)).orElseThrow().id());
            assertEquals(2, jpaRepository.findAll().stream()
                    .filter(entity -> entity.getId().equals(first.id()) || entity.getId().equals(second.id()))
                    .count());
        } finally {
            executor.shutdownNow();
        }
    }

    private Attempt attempt(CatalogService catalogService, CyclicBarrier preCheckBarrier) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertTrue(repository.findByName(catalogService.name()).isEmpty());
                await(preCheckBarrier);
                repository.save(catalogService);
            });
            return new Attempt(catalogService, null);
        } catch (RuntimeException exception) {
            return new Attempt(catalogService, exception);
        }
    }

    private RenameAttempt attemptRename(UUID id, String displayName, CyclicBarrier preCheckBarrier) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                CatalogService locked = repository.findByIdForUpdate(id).orElseThrow();
                CatalogServiceName newName = new CatalogServiceName(displayName);
                assertTrue(repository.findByName(newName).isEmpty());
                await(preCheckBarrier);
                locked.rename(newName);
                repository.save(locked);
            });
            return new RenameAttempt(id, null);
        } catch (RuntimeException exception) {
            return new RenameAttempt(id, exception);
        }
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    private static CatalogService service(String name) {
        return CatalogService.create(
                new CatalogServiceName(name),
                new Money(new BigDecimal("120.00"), CurrencyCode.BRL));
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("Tempo excedido aguardando pre-check concorrente", exception);
        }
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

    private record Attempt(CatalogService catalogService, RuntimeException failure) {

        private boolean successful() {
            return failure == null;
        }
    }

    private record RenameAttempt(UUID catalogServiceId, RuntimeException failure) {

        private boolean successful() {
            return failure == null;
        }
    }
}
