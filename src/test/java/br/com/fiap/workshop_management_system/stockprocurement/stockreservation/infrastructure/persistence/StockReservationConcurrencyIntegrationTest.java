package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase.DeactivateStockItemUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase.ConsumeStockReservationUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class StockReservationConcurrencyIntegrationTest {

    @Autowired
    private StockReservationApi stockReservationApi;

    @Autowired
    private ConsumeStockReservationUseCase consumeStockReservationUseCase;

    @Autowired
    private DeactivateStockItemUseCase deactivateStockItemUseCase;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private StockReservationRepository stockReservationRepository;

    @Test
    void onlyOneExecutionReservesTheLastAvailableUnit() throws Exception {
        StockItem stockItem = stockItem(1);
        stockItemRepository.save(stockItem);

        List<ReserveStockItemsResult> results = concurrently(
                () -> reserve(UUID.randomUUID(), stockItem.id(), 1),
                () -> reserve(UUID.randomUUID(), stockItem.id(), 1));

        assertEquals(1, results.stream()
                .filter(result -> result.outcome() == ReservationAttemptOutcome.RESERVED)
                .count());
        assertEquals(0, stockItemRepository.findById(stockItem.id()).orElseThrow().availableQuantity().value());
        assertFalse(stockItemRepository.findById(stockItem.id()).orElseThrow().availableQuantity().value() < 0);
    }

    @Test
    void concurrentRetriesForTheSameExecutionCreateOneReservationAndOneDiscount() throws Exception {
        StockItem stockItem = stockItem(1);
        stockItemRepository.save(stockItem);
        UUID executionId = UUID.randomUUID();

        List<ReserveStockItemsResult> results = concurrently(
                () -> reserve(executionId, stockItem.id(), 1),
                () -> reserve(executionId, stockItem.id(), 1));

        assertTrue(results.stream().allMatch(result -> result.outcome() == ReservationAttemptOutcome.RESERVED));
        assertEquals(1, results.stream().filter(ReserveStockItemsResult::newlyCreated).count());
        assertEquals(results.getFirst().reservationId(), results.get(1).reservationId());
        assertEquals(0, stockItemRepository.findById(stockItem.id()).orElseThrow().availableQuantity().value());
        assertNotNull(stockReservationRepository.findByServiceExecutionId(executionId).orElseThrow());
    }

    @Test
    void insufficientItemInAMultiItemReservationDoesNotDiscountAnyItem() {
        StockItem available = stockItem(2);
        StockItem insufficient = stockItem(1);
        stockItemRepository.save(available);
        stockItemRepository.save(insufficient);

        List<ReserveStockItem> requestedItems = List.of(
                new ReserveStockItem(available.id(), 2),
                new ReserveStockItem(insufficient.id(), 2));
        ReserveStockItemsResult result = stockReservationApi.reserveAll(List.of(
                new ReserveStockItemsCommand(UUID.randomUUID(), requestedItems))).getFirst();

        assertEquals(ReservationAttemptOutcome.NOT_RESERVED, result.outcome());
        assertEquals(2, stockItemRepository.findById(available.id()).orElseThrow().availableQuantity().value());
        assertEquals(1, stockItemRepository.findById(insufficient.id()).orElseThrow().availableQuantity().value());
    }

    @Test
    void concurrentDeactivationDoesNotOverwriteAReservedBalance() throws Exception {
        StockItem stockItem = stockItem(1);
        stockItemRepository.save(stockItem);
        UUID executionId = UUID.randomUUID();

        List<Object> results = concurrently(
                () -> reserve(executionId, stockItem.id(), 1),
                () -> {
                    deactivateStockItemUseCase.execute(stockItem.id());
                    return null;
                });

        ReserveStockItemsResult reservationResult = (ReserveStockItemsResult) results.getFirst();
        StockItem persisted = stockItemRepository.findById(stockItem.id()).orElseThrow();
        assertFalse(persisted.active());
        assertEquals(reservationResult.outcome() == ReservationAttemptOutcome.RESERVED ? 0 : 1,
                persisted.availableQuantity().value());
    }

    @Test
    void concurrentConsumptionKeepsTheFirstConsumptionTimestamp() throws Exception {
        StockItem stockItem = stockItem(1);
        stockItemRepository.save(stockItem);
        UUID reservationId = reserve(UUID.randomUUID(), stockItem.id(), 1).reservationId();

        List<java.time.Instant> consumedAts = concurrently(
                () -> consumeStockReservationUseCase.execute(reservationId).consumedAt(),
                () -> consumeStockReservationUseCase.execute(reservationId).consumedAt());

        assertNotNull(consumedAts.getFirst());
        assertEquals(consumedAts.getFirst(), consumedAts.get(1));
    }

    private ReserveStockItemsResult reserve(UUID executionId, UUID stockItemId, int quantity) {
        return stockReservationApi.reserveAll(List.of(new ReserveStockItemsCommand(
                executionId, List.of(new ReserveStockItem(stockItemId, quantity))))).getFirst();
    }

    private StockItem stockItem(int quantity) {
        return StockItem.create(
                new Sku("CONCURRENT-" + UUID.randomUUID()),
                "Concurrent stock item",
                StockItemType.PART,
                new Price(BigDecimal.ONE, CurrencyCode.BRL),
                new Quantity(quantity));
    }

    @SafeVarargs
    private final <T> List<T> concurrently(Callable<T>... actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.length);
        CyclicBarrier barrier = new CyclicBarrier(actions.length);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> action : actions) {
                futures.add(executor.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    return action.call();
                }));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(20, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
