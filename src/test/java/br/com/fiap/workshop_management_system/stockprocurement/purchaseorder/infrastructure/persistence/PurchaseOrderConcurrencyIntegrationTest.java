package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.LowStockPurchaseDemandCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.PurchaseDemandApi;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.CreatePurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.PurchaseOrderLineCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.CreatePurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalSupplierGateway;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase.CreatePurchaseOrderUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandNotSelectableException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandSearchCriteria;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.CreateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.PriceDto;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.CreateStockItemUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@Import(PurchaseOrderConcurrencyIntegrationTest.SupplierTestConfiguration.class)
class PurchaseOrderConcurrencyIntegrationTest {

    @Autowired
    private CreateStockItemUseCase createStockItemUseCase;

    @Autowired
    private CreatePurchaseOrderUseCase createPurchaseOrderUseCase;

    @Autowired
    private PurchaseDemandApi purchaseDemandApi;

    @Autowired
    private PurchaseDemandRepository demandRepository;

    @Test
    void concurrentRetriesWithTheSameKeyConvergeToOnePurchaseOrder() throws Exception {
        UUID stockItemId = createStockItem();
        UUID idempotencyKey = UUID.randomUUID();
        CreatePurchaseOrderCommand command = command(stockItemId, List.of());

        List<Attempt> attempts = runConcurrently(
                () -> createPurchaseOrderUseCase.execute(idempotencyKey, command),
                () -> createPurchaseOrderUseCase.execute(idempotencyKey, command));

        CreatePurchaseOrderResult first = attempts.get(0).result();
        CreatePurchaseOrderResult second = attempts.get(1).result();
        assertEquals(first.purchaseOrder().id(), second.purchaseOrder().id());
        assertEquals(first.purchaseOrder().externalReference(), second.purchaseOrder().externalReference());
    }

    @Test
    void concurrentDifferentOrdersCannotClaimTheSameDemand() throws Exception {
        UUID stockItemId = createStockItem();
        purchaseDemandApi.recordLowStock(new LowStockPurchaseDemandCommand(
                UUID.randomUUID(), stockItemId, 0, 2));
        UUID demandId = demandRepository.searchOpen(new PurchaseDemandSearchCriteria(null, stockItemId))
                .getFirst().id();
        CreatePurchaseOrderCommand command = command(stockItemId, List.of(demandId));

        List<Attempt> attempts = runConcurrently(
                () -> createPurchaseOrderUseCase.execute(UUID.randomUUID(), command),
                () -> createPurchaseOrderUseCase.execute(UUID.randomUUID(), command));

        long successes = attempts.stream().filter(attempt -> attempt.result() != null).count();
        Throwable failure = attempts.stream()
                .map(Attempt::failure)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
        assertEquals(1, successes);
        assertInstanceOf(PurchaseDemandNotSelectableException.class, failure);
    }

    private List<Attempt> runConcurrently(ThrowingSupplier first, ThrowingSupplier second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Attempt> firstFuture = executor.submit(() -> attempt(start, first));
            Future<Attempt> secondFuture = executor.submit(() -> attempt(start, second));
            start.countDown();
            return List.of(get(firstFuture), get(secondFuture));
        }
    }

    private Attempt attempt(CountDownLatch start, ThrowingSupplier supplier) throws InterruptedException {
        start.await();
        try {
            return new Attempt(supplier.get(), null);
        } catch (Throwable failure) {
            return new Attempt(null, failure);
        }
    }

    private Attempt get(Future<Attempt> future) throws InterruptedException, ExecutionException {
        return future.get();
    }

    private UUID createStockItem() {
        return createStockItemUseCase.execute(new CreateStockItemRequest(
                "PO-CONCURRENT-" + System.nanoTime(),
                "Concurrent purchase item",
                StockItemType.PART,
                new PriceDto(BigDecimal.TEN, CurrencyCode.BRL),
                0)).id();
    }

    private CreatePurchaseOrderCommand command(UUID stockItemId, List<UUID> demandIds) {
        return new CreatePurchaseOrderCommand(
                demandIds,
                List.of(new PurchaseOrderLineCommand(stockItemId, 2)));
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        CreatePurchaseOrderResult get();
    }

    private record Attempt(CreatePurchaseOrderResult result, Throwable failure) {
    }

    @TestConfiguration
    static class SupplierTestConfiguration {

        @Bean
        @Primary
        ExternalSupplierGateway concurrentSupplierGateway() {
            return command -> new ExternalPurchaseOrderResult.Accepted(
                    "SUP-" + command.idempotencyKey());
        }
    }
}
