package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailability;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailabilityApi;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto
        .UpdateCatalogServiceBasePriceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.MoneyDto;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase
        .ArchiveCatalogServiceUseCase;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase
        .UpdateCatalogServiceBasePriceUseCase;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceArchivedException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CatalogServiceLifecycleConcurrencyIntegrationTest {

    @Autowired
    private CatalogServiceRepository repository;

    @Autowired
    private CatalogServiceAvailabilityApi availabilityApi;

    @Autowired
    private ArchiveCatalogServiceUseCase archiveUseCase;

    @Autowired
    private UpdateCatalogServiceBasePriceUseCase updateBasePriceUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void requiresAConsumerTransactionAndReportsAllAvailabilityStates() {
        CatalogService active = service("Ativo " + UUID.randomUUID(), true);
        CatalogService archived = service("Arquivado " + UUID.randomUUID(), false);
        repository.save(active);
        repository.save(archived);

        assertThatThrownBy(() -> availabilityApi.checkForNewWork(active.id()))
                .isInstanceOf(IllegalTransactionStateException.class);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThat(transaction.<CatalogServiceAvailability>execute(
                status -> availabilityApi.checkForNewWork(active.id())))
                .isEqualTo(CatalogServiceAvailability.ACTIVE);
        assertThat(transaction.<CatalogServiceAvailability>execute(
                status -> availabilityApi.checkForNewWork(archived.id())))
                .isEqualTo(CatalogServiceAvailability.ARCHIVED);
        assertThat(transaction.<CatalogServiceAvailability>execute(
                status -> availabilityApi.checkForNewWork(UUID.randomUUID())))
                .isEqualTo(CatalogServiceAvailability.NOT_FOUND);
    }

    @Test
    void keepsTheAvailabilityLockUntilTheConsumerTransactionCommits() throws Exception {
        CatalogService service = service("Diagnóstico concorrente " + UUID.randomUUID(), true);
        repository.save(service);
        CountDownLatch availabilityChecked = new CountDownLatch(1);
        CountDownLatch releaseConsumer = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<CatalogServiceAvailability> check = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        CatalogServiceAvailability availability = availabilityApi.checkForNewWork(service.id());
                        availabilityChecked.countDown();
                        await(releaseConsumer);
                        return availability;
                    }));
            assertTrue(availabilityChecked.await(5, TimeUnit.SECONDS));

            Future<?> archive = executor.submit(() -> archiveUseCase.execute(service.id()));

            assertThrows(TimeoutException.class, () -> archive.get(250, TimeUnit.MILLISECONDS));
            releaseConsumer.countDown();
            assertThat(check.get(5, TimeUnit.SECONDS)).isEqualTo(CatalogServiceAvailability.ACTIVE);
            archive.get(5, TimeUnit.SECONDS);
            assertThat(repository.findById(service.id()).orElseThrow().active()).isFalse();
        } finally {
            releaseConsumer.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsAnUpdateThatStartsAfterAnUncommittedArchive() throws Exception {
        CatalogService service = service("Preço concorrente " + UUID.randomUUID(), true);
        repository.save(service);
        CountDownLatch archiveWritten = new CountDownLatch(1);
        CountDownLatch releaseArchive = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> archive = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        CatalogService locked = repository.findByIdForUpdate(service.id()).orElseThrow();
                        locked.archive();
                        repository.save(locked);
                        archiveWritten.countDown();
                        await(releaseArchive);
                    }));
            assertTrue(archiveWritten.await(5, TimeUnit.SECONDS));

            Future<?> update = executor.submit(() -> updateBasePriceUseCase.execute(
                    service.id(),
                    new UpdateCatalogServiceBasePriceRequest(
                            new MoneyDto(new BigDecimal("199.90"), CurrencyCode.BRL))));

            assertThrows(TimeoutException.class, () -> update.get(250, TimeUnit.MILLISECONDS));
            releaseArchive.countDown();
            archive.get(5, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(
                    ExecutionException.class, () -> update.get(5, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOf(CatalogServiceArchivedException.class);
            CatalogService stored = repository.findById(service.id()).orElseThrow();
            assertThat(stored.active()).isFalse();
            assertThat(stored.basePrice().value()).isEqualByComparingTo("100.00");
        } finally {
            releaseArchive.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void releasesTheNameOnlyAfterArchiveCommit() {
        String name = "Nome liberado " + UUID.randomUUID();
        CatalogService original = service(name, true);
        repository.save(original);

        CatalogServiceNameAlreadyExistsException beforeArchive = assertThrows(
                CatalogServiceNameAlreadyExistsException.class,
                () -> repository.save(service(name.toUpperCase(), true)));
        assertThat(beforeArchive.existingId()).isEqualTo(original.id());

        archiveUseCase.execute(original.id());
        CatalogService replacement = service(name.toUpperCase(), true);
        repository.save(replacement);

        assertThat(repository.findActiveByName(new CatalogServiceName(name)).orElseThrow().id())
                .isEqualTo(replacement.id());
        assertThat(repository.findById(original.id()).orElseThrow().active()).isFalse();
    }

    private static CatalogService service(String name, boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName(name),
                new Money(new BigDecimal("100.00"), CurrencyCode.BRL),
                active);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for the concurrent Catalog Service operation");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Concurrent Catalog Service operation was interrupted", exception);
        }
    }
}
