package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationIssueReason;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationConflictException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationCreatedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.event.StockReservationNotReservedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReserveStockItemsUseCaseTest {

    private final InMemoryStockItemRepository stockItems = new InMemoryStockItemRepository();
    private final InMemoryStockReservationRepository reservations = new InMemoryStockReservationRepository();
    private final ReserveStockItemsUseCase useCase = new ReserveStockItemsUseCase(
            stockItems,
            reservations,
            Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void reservesAllItemsAndConsolidatesRepeatedLinesBeforeChangingBalance() {
        StockItem oil = stockItem("OIL", 5);
        StockItem filter = stockItem("FILTER", 2);
        stockItems.save(oil);
        stockItems.save(filter);
        UUID executionId = UUID.randomUUID();

        ReserveStockItemsResult result = useCase.reserveAll(List.of(new ReserveStockItemsCommand(
                executionId,
                List.of(new ReserveStockItem(oil.id(), 1), new ReserveStockItem(filter.id(), 2),
                        new ReserveStockItem(oil.id(), 2))))).getFirst();

        assertEquals(ReservationAttemptOutcome.RESERVED, result.outcome());
        assertTrue(result.newlyCreated());
        assertEquals(2, result.items().size());
        assertEquals(2, stockItems.findById(oil.id()).orElseThrow().availableQuantity().value());
        assertEquals(0, stockItems.findById(filter.id()).orElseThrow().availableQuantity().value());
        assertEquals(1, reservations.byId.size());
    }

    @Test
    void returnsEveryIssueWithoutCreatingAPartialReservationOrDiscountingAnyItem() {
        StockItem inactive = stockItem("INACTIVE", 3);
        inactive.deactivate();
        StockItem insufficient = stockItem("INSUFFICIENT", 1);
        stockItems.save(inactive);
        stockItems.save(insufficient);

        ReserveStockItemsResult result = useCase.reserveAll(List.of(new ReserveStockItemsCommand(
                UUID.randomUUID(),
                List.of(new ReserveStockItem(UUID.randomUUID(), 1), new ReserveStockItem(inactive.id(), 1),
                        new ReserveStockItem(insufficient.id(), 2))))).getFirst();

        assertEquals(ReservationAttemptOutcome.NOT_RESERVED, result.outcome());
        assertEquals(3, result.issues().size());
        assertTrue(result.issues().stream().map(issue -> issue.reason()).toList()
                .containsAll(List.of(
                        StockReservationIssueReason.STOCK_ITEM_NOT_FOUND,
                        StockReservationIssueReason.STOCK_ITEM_INACTIVE,
                        StockReservationIssueReason.INSUFFICIENT_QUANTITY)));
        assertEquals(3, stockItems.findById(inactive.id()).orElseThrow().availableQuantity().value());
        assertEquals(1, stockItems.findById(insufficient.id()).orElseThrow().availableQuantity().value());
        assertTrue(reservations.byId.isEmpty());
    }

    @Test
    void handlesIndependentResultsInTheSameBatch() {
        StockItem item = stockItem("SHARED", 2);
        stockItems.save(item);

        List<ReserveStockItemsResult> results = useCase.reserveAll(List.of(
                command(UUID.randomUUID(), item.id(), 2),
                command(UUID.randomUUID(), item.id(), 1)));

        assertEquals(ReservationAttemptOutcome.RESERVED, results.get(0).outcome());
        assertEquals(ReservationAttemptOutcome.NOT_RESERVED, results.get(1).outcome());
        assertEquals(0, stockItems.findById(item.id()).orElseThrow().availableQuantity().value());
        assertEquals(1, reservations.byId.size());
    }

    @Test
    void returnsAnExistingReservationWithoutDiscountingAgainAndRejectsDifferentLines() {
        StockItem item = stockItem("IDEMPOTENT", 3);
        stockItems.save(item);
        UUID executionId = UUID.randomUUID();
        ReserveStockItemsCommand command = command(executionId, item.id(), 2);

        ReserveStockItemsResult initial = useCase.reserveAll(List.of(command)).getFirst();
        ReserveStockItemsResult repeated = useCase.reserveAll(List.of(command)).getFirst();

        assertTrue(initial.newlyCreated());
        assertFalse(repeated.newlyCreated());
        assertEquals(initial.reservationId(), repeated.reservationId());
        assertEquals(1, stockItems.findById(item.id()).orElseThrow().availableQuantity().value());
        assertThrows(StockReservationConflictException.class,
                () -> useCase.reserveAll(List.of(command(executionId, item.id(), 1))));
    }

    @Test
    void publishesCreationAndUnavailabilityEventsWithoutRepeatingTheCreationEvent() {
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ReserveStockItemsUseCase eventPublishingUseCase = new ReserveStockItemsUseCase(
                stockItems,
                reservations,
                Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC),
                eventPublisher);
        StockItem item = stockItem("EVENT", 2);
        stockItems.save(item);
        UUID executionId = UUID.randomUUID();
        ReserveStockItemsCommand command = command(executionId, item.id(), 2);

        ReserveStockItemsResult created = eventPublishingUseCase.reserveAll(List.of(command)).getFirst();
        eventPublishingUseCase.reserveAll(List.of(command));
        ReserveStockItemsResult unavailable = eventPublishingUseCase.reserveAll(List.of(command(
                UUID.randomUUID(), item.id(), 1))).getFirst();

        verify(eventPublisher).publishEvent(new StockReservationCreatedEvent(
                created.reservationId(), executionId, created.items()));
        verify(eventPublisher).publishEvent(new StockReservationNotReservedEvent(
                unavailable.serviceExecutionId(), unavailable.issues()));
    }

    private ReserveStockItemsCommand command(UUID executionId, UUID stockItemId, int quantity) {
        return new ReserveStockItemsCommand(executionId, List.of(new ReserveStockItem(stockItemId, quantity)));
    }

    private StockItem stockItem(String sku, int quantity) {
        return StockItem.create(
                new Sku(sku),
                sku + " item",
                StockItemType.PART,
                new Price(BigDecimal.ONE, CurrencyCode.BRL),
                new Quantity(quantity));
    }

    private static final class InMemoryStockItemRepository implements StockItemRepository {
        private final Map<UUID, StockItem> byId = new HashMap<>();

        @Override
        public Optional<StockItem> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<StockItem> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public List<StockItem> findAllByIdForUpdate(List<UUID> ids) {
            return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public boolean existsBySku(Sku sku) {
            return byId.values().stream().anyMatch(item -> item.sku().equals(sku));
        }

        @Override
        public List<StockItem> search(StockItemSearchCriteria criteria) {
            return List.copyOf(byId.values());
        }

        @Override
        public void save(StockItem stockItem) {
            byId.put(stockItem.id(), stockItem);
        }
    }

    private static final class InMemoryStockReservationRepository implements StockReservationRepository {
        private final Map<UUID, StockReservation> byId = new HashMap<>();

        @Override
        public Optional<StockReservation> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<StockReservation> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<StockReservation> findByServiceExecutionId(UUID serviceExecutionId) {
            return byId.values().stream()
                    .filter(reservation -> reservation.serviceExecutionId().equals(serviceExecutionId))
                    .findFirst();
        }

        @Override
        public List<StockReservation> findByServiceExecutionIdIn(Collection<UUID> serviceExecutionIds) {
            return byId.values().stream()
                    .filter(reservation -> serviceExecutionIds.contains(reservation.serviceExecutionId()))
                    .sorted(Comparator.comparing(StockReservation::serviceExecutionId))
                    .toList();
        }

        @Override
        public void save(StockReservation stockReservation) {
            byId.put(stockReservation.id(), stockReservation);
        }
    }
}
