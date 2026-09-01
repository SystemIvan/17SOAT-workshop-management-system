package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.LowStockPurchaseDemandCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.PurchaseDemandApi;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.CreatePurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.PurchaseOrderLineCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.CreatePurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalPurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port.ExternalSupplierGateway;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase.CreatePurchaseOrderUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase.ClosePurchaseOrderUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto.ReceivePurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.usecase.ReceivePurchaseOrderUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandNotSelectableException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandSearchCriteria;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.CreateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.PriceDto;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.CreateStockItemUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
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
    private ClosePurchaseOrderUseCase closePurchaseOrderUseCase;

    @Autowired
    private ReceivePurchaseOrderUseCase receivePurchaseOrderUseCase;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationApi stockReservationApi;

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

    @Test
    void concurrentClosingsConvergeToOneAuditRecord() throws Exception {
        UUID stockItemId = createStockItem();
        CreatePurchaseOrderResult creation = createPurchaseOrderUseCase.execute(
                UUID.randomUUID(), command(stockItemId, List.of()));

        List<ClosingAttempt> attempts = runClosingsConcurrently(
                () -> closePurchaseOrderUseCase.execute(creation.purchaseOrder().id(), UUID.randomUUID()),
                () -> closePurchaseOrderUseCase.execute(creation.purchaseOrder().id(), UUID.randomUUID()));

        assertEquals(attempts.get(0).closedAt(), attempts.get(1).closedAt());
        assertEquals(attempts.get(0).closedByUserAccountId(), attempts.get(1).closedByUserAccountId());
    }

    @Test
    void concurrentReceiptsConvergeToOneReceiptAndOneBalanceIncrement() throws Exception {
        UUID stockItemId = createStockItem();
        CreatePurchaseOrderResult creation = createPurchaseOrderUseCase.execute(
                UUID.randomUUID(), command(stockItemId, List.of()));
        closePurchaseOrderUseCase.execute(creation.purchaseOrder().id(), UUID.randomUUID());

        List<ReceiptAttempt> attempts = runReceiptsConcurrently(
                () -> receivePurchaseOrderUseCase.execute(creation.purchaseOrder().id(), UUID.randomUUID()),
                () -> receivePurchaseOrderUseCase.execute(creation.purchaseOrder().id(), UUID.randomUUID()));

        assertEquals(attempts.get(0).receipt().id(), attempts.get(1).receipt().id());
        assertEquals(2, stockItemRepository.findById(stockItemId).orElseThrow().availableQuantity().value());
    }

    @Test
    void concurrentReceiptsForDifferentOrdersPreserveBothBalanceIncrements() throws Exception {
        UUID stockItemId = createStockItem();
        UUID firstPurchaseOrderId = createAndCloseOrder(stockItemId);
        UUID secondPurchaseOrderId = createAndCloseOrder(stockItemId);

        runReceiptsConcurrently(
                () -> receivePurchaseOrderUseCase.execute(firstPurchaseOrderId, UUID.randomUUID()),
                () -> receivePurchaseOrderUseCase.execute(secondPurchaseOrderId, UUID.randomUUID()));

        assertEquals(4, stockItemRepository.findById(stockItemId).orElseThrow().availableQuantity().value());
    }

    @Test
    void concurrentReceiptAndReservationDoNotLoseABalanceUpdate() throws Exception {
        UUID stockItemId = createStockItem(1);
        UUID purchaseOrderId = createAndCloseOrder(stockItemId);
        UUID serviceExecutionId = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ReceivePurchaseOrderResult> receiptFuture = executor.submit(() -> {
                start.await();
                return receivePurchaseOrderUseCase.execute(purchaseOrderId, UUID.randomUUID());
            });
            Future<ReserveStockItemsResult> reservationFuture = executor.submit(() -> {
                start.await();
                return stockReservationApi.reserveAll(List.of(new ReserveStockItemsCommand(
                        serviceExecutionId, List.of(new ReserveStockItem(stockItemId, 1))))).getFirst();
            });
            start.countDown();
            receiptFuture.get();
            ReserveStockItemsResult reservation = reservationFuture.get();

            int expectedBalance = reservation.outcome() == ReservationAttemptOutcome.RESERVED ? 2 : 3;
            assertEquals(expectedBalance, stockItemRepository.findById(stockItemId).orElseThrow()
                    .availableQuantity().value());
        }
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

    private List<ClosingAttempt> runClosingsConcurrently(
            ThrowingClosingSupplier first, ThrowingClosingSupplier second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ClosingAttempt> firstFuture = executor.submit(() -> {
                start.await();
                return new ClosingAttempt(first.get());
            });
            Future<ClosingAttempt> secondFuture = executor.submit(() -> {
                start.await();
                return new ClosingAttempt(second.get());
            });
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        }
    }

    private List<ReceiptAttempt> runReceiptsConcurrently(
            ThrowingReceiptSupplier first, ThrowingReceiptSupplier second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<ReceiptAttempt> firstFuture = executor.submit(() -> {
                start.await();
                return new ReceiptAttempt(first.get());
            });
            Future<ReceiptAttempt> secondFuture = executor.submit(() -> {
                start.await();
                return new ReceiptAttempt(second.get());
            });
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        }
    }

    private UUID createAndCloseOrder(UUID stockItemId) {
        CreatePurchaseOrderResult creation = createPurchaseOrderUseCase.execute(
                UUID.randomUUID(), command(stockItemId, List.of()));
        closePurchaseOrderUseCase.execute(creation.purchaseOrder().id(), UUID.randomUUID());
        return creation.purchaseOrder().id();
    }

    private UUID createStockItem() {
        return createStockItem(0);
    }

    private UUID createStockItem(int availableQuantity) {
        return createStockItemUseCase.execute(new CreateStockItemRequest(
                "PO-CONCURRENT-" + System.nanoTime(),
                "Concurrent purchase item",
                StockItemType.PART,
                new PriceDto(BigDecimal.TEN, CurrencyCode.BRL),
                availableQuantity)).id();
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

    @FunctionalInterface
    private interface ThrowingClosingSupplier {
        PurchaseOrderResponse get();
    }

    @FunctionalInterface
    private interface ThrowingReceiptSupplier {
        ReceivePurchaseOrderResult get();
    }

    private record Attempt(CreatePurchaseOrderResult result, Throwable failure) {
    }

    private record ClosingAttempt(java.time.Instant closedAt, UUID closedByUserAccountId) {

        private ClosingAttempt(PurchaseOrderResponse response) {
            this(response.closedAt(), response.closedByUserAccountId());
        }
    }

    private record ReceiptAttempt(StockReceipt receipt) {

        private ReceiptAttempt(ReceivePurchaseOrderResult result) {
            this(result.receipt());
        }
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
