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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    private record Attempt(CatalogService catalogService, RuntimeException failure) {

        private boolean successful() {
            return failure == null;
        }
    }
}
