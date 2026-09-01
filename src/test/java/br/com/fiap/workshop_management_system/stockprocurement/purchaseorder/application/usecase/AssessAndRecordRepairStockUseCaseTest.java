package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentExecution;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAvailabilityStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandSearchCriteria;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssessAndRecordRepairStockUseCaseTest {

    @Test
    void createsDemandForConcreteShortageWithoutChangingAvailability() {
        StockItem stockItem = StockItem.create(new Sku("FILTER-001"), "Filter", StockItemType.PART,
                new Price(BigDecimal.ONE, CurrencyCode.BRL), new Quantity(1));
        InMemoryDemands demands = new InMemoryDemands();
        var useCase = new AssessAndRecordRepairStockUseCase(new SingleStockItemRepository(stockItem), demands,
                Clock.fixed(Instant.parse("2026-08-25T17:00:00Z"), ZoneOffset.UTC));

        var result = useCase.assessAndRecord(new RepairStockAssessmentCommand(List.of(
                new RepairStockAssessmentExecution(UUID.randomUUID(), List.of(
                        new RepairStockAssessmentLine(stockItem.id(), 3))))));

        assertEquals(RepairStockAvailabilityStatus.INSUFFICIENT_QUANTITY,
                result.executions().getFirst().lines().getFirst().status());
        assertEquals(2, demands.saved.getFirst().suggestedQuantity());
        assertEquals(1, stockItem.availableQuantity().value());
    }

    private static final class SingleStockItemRepository implements StockItemRepository {
        private final StockItem item;
        private SingleStockItemRepository(StockItem item) { this.item = item; }
        public Optional<StockItem> findById(UUID id) { return id.equals(item.id()) ? Optional.of(item) : Optional.empty(); }
        public Optional<StockItem> findByIdForUpdate(UUID id) { return findById(id); }
        public List<StockItem> findAllByIdForUpdate(List<UUID> ids) { return ids.contains(item.id()) ? List.of(item) : List.of(); }
        public boolean existsBySku(Sku sku) { return item.sku().equals(sku); }
        public List<StockItem> search(StockItemSearchCriteria criteria) { return List.of(item); }
        public void save(StockItem stockItem) { }
    }

    private static final class InMemoryDemands implements PurchaseDemandRepository {
        private final List<PurchaseDemand> saved = new java.util.ArrayList<>();
        public Optional<PurchaseDemand> findById(UUID id) { return saved.stream().filter(demand -> demand.id().equals(id)).findFirst(); }
        public Optional<PurchaseDemand> findEquivalentForUpdate(PurchaseDemandOrigin origin, UUID reference, UUID itemId) {
            return saved.stream().filter(demand -> demand.origin() == origin && demand.originReferenceId().equals(reference)
                    && demand.stockItemId().equals(itemId)).findFirst();
        }
        public List<PurchaseDemand> findAllByIdForUpdate(List<UUID> ids) { return saved.stream().filter(demand -> ids.contains(demand.id())).toList(); }
        public List<PurchaseDemand> findOpenByOriginReferenceAndStockItems(PurchaseDemandOrigin origin, UUID reference, Collection<UUID> ids) { return List.of(); }
        public List<PurchaseDemand> searchOpen(PurchaseDemandSearchCriteria criteria) { return List.of(); }
        public void save(PurchaseDemand demand) { if (!saved.contains(demand)) saved.add(demand); }
        public void saveAll(Collection<PurchaseDemand> demands) { demands.forEach(this::save); }
    }
}
