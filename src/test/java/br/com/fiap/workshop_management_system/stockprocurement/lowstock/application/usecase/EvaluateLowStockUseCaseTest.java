package br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockClosureReason;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrence;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository.LowStockOccurrenceRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.LowStockPurchaseDemandCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.LowStockPurchaseDemandResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.PurchaseDemandApi;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.PurchaseDemandStatusView;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.LowStockPolicy;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluateLowStockUseCaseTest {

    @Test
    void opensUpdatesAndClosesOnlyOneOccurrenceForTheSameLowStockCycle() {
        InMemoryOccurrenceRepository occurrences = new InMemoryOccurrenceRepository();
        PurchaseDemandApi demandApi = mock(PurchaseDemandApi.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        UUID demandId = UUID.randomUUID();
        when(demandApi.recordLowStock(any())).thenReturn(new LowStockPurchaseDemandResult(
                demandId, PurchaseDemandStatusView.OPEN));
        EvaluateLowStockUseCase useCase = new EvaluateLowStockUseCase(occurrences, demandApi, eventPublisher,
                Clock.fixed(Instant.parse("2026-08-26T18:00:00Z"), ZoneOffset.UTC));
        StockItem item = StockItem.create(new Sku("LOW"), "Low item", StockItemType.PART,
                new Price(BigDecimal.ONE, CurrencyCode.BRL), new Quantity(4),
                new LowStockPolicy(new Quantity(5), new Quantity(12)));

        useCase.evaluateLockedStockItem(item);
        LowStockOccurrence opened = occurrences.findOpenByStockItemIdForUpdate(item.id()).orElseThrow();
        assertEquals(demandId, opened.purchaseDemandId());
        verify(demandApi).recordLowStock(new LowStockPurchaseDemandCommand(opened.id(), item.id(), 4, 8));

        item.reserve(new Quantity(1));
        useCase.evaluateLockedStockItem(item);
        assertEquals(opened.id(), occurrences.findOpenByStockItemIdForUpdate(item.id()).orElseThrow().id());
        verify(demandApi).recordLowStock(new LowStockPurchaseDemandCommand(opened.id(), item.id(), 3, 9));

        item.receive(new Quantity(2));
        useCase.evaluateLockedStockItem(item);
        assertEquals(LowStockClosureReason.STOCK_RECOVERED, occurrences.byId.get(opened.id()).closureReason());
        verify(demandApi).resolveLowStock(any());
    }

    private static final class InMemoryOccurrenceRepository implements LowStockOccurrenceRepository {
        private final Map<UUID, LowStockOccurrence> byId = new HashMap<>();

        @Override
        public Optional<LowStockOccurrence> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Optional<LowStockOccurrence> findOpenByStockItemIdForUpdate(UUID stockItemId) {
            return byId.values().stream().filter(occurrence -> occurrence.stockItemId().equals(stockItemId))
                    .filter(occurrence -> occurrence.status().name().equals("OPEN")).findFirst();
        }

        @Override
        public List<LowStockOccurrence> findOpenByStockItemIds(Collection<UUID> stockItemIds) {
            return byId.values().stream().filter(occurrence -> stockItemIds.contains(occurrence.stockItemId()))
                    .filter(occurrence -> occurrence.status().name().equals("OPEN")).toList();
        }

        @Override
        public Optional<LowStockOccurrence> findByPurchaseDemandIdForUpdate(UUID purchaseDemandId) {
            return byId.values().stream().filter(occurrence -> occurrence.purchaseDemandId().equals(purchaseDemandId))
                    .findFirst();
        }

        @Override
        public void save(LowStockOccurrence occurrence) {
            byId.put(occurrence.id(), occurrence);
        }
    }
}
