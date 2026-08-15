package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.CreateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.PriceDto;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.UpdateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception
        .StockItemSkuAlreadyExistsException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemInactiveException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockItemUseCaseTest {
    @Test
    void createsAndRejectsDuplicateNormalizedSku() {
        InMemoryRepository repository = new InMemoryRepository();
        CreateStockItemUseCase useCase = new CreateStockItemUseCase(repository);
        useCase.execute(request("filter-001"));
        assertThrows(StockItemSkuAlreadyExistsException.class, () -> useCase.execute(request(" FILTER-001 ")));
    }

    @Test
    void findsInactiveItemButDoesNotUpdateItAndDeactivationIsIdempotent() {
        InMemoryRepository repository = new InMemoryRepository();
        CreateStockItemUseCase create = new CreateStockItemUseCase(repository);
        UUID id = create.execute(request("SKU-1")).id();
        DeactivateStockItemUseCase deactivate = new DeactivateStockItemUseCase(repository);
        deactivate.execute(id);
        deactivate.execute(id);
        assertFalse(new GetStockItemUseCase(repository).execute(id).active());
        assertThrows(StockItemInactiveException.class,
                () -> new UpdateStockItemUseCase(repository).execute(id, new UpdateStockItemRequest("Other", null)));
    }

    @Test
    void forwardsSearchCriteriaAndReportsNotFound() {
        InMemoryRepository repository = new InMemoryRepository();
        StockItemSearchCriteria criteria = new StockItemSearchCriteria("filter", SetBuilder.types(), true, true);
        new SearchStockItemsUseCase(repository).execute(criteria);
        assertEquals(criteria, repository.criteria);
        assertThrows(StockItemNotFoundException.class,
                () -> new GetStockItemUseCase(repository).execute(UUID.randomUUID()));
    }

    private static CreateStockItemRequest request(String sku) {
        return new CreateStockItemRequest(sku, "Oil filter", StockItemType.PART,
                new PriceDto(new BigDecimal("45.90"), CurrencyCode.BRL), 2);
    }

    private static final class SetBuilder {
        private static java.util.Set<StockItemType> types() { return java.util.Set.of(StockItemType.PART); }
    }

    private static final class InMemoryRepository implements StockItemRepository {
        private final List<StockItem> items = new ArrayList<>();
        private StockItemSearchCriteria criteria;
        public Optional<StockItem> findById(UUID id) {
            return items.stream().filter(item -> item.id().equals(id)).findFirst();
        }
        public boolean existsBySku(Sku sku) { return items.stream().anyMatch(item -> item.sku().equals(sku)); }
        public List<StockItem> search(StockItemSearchCriteria criteria) {
            this.criteria = criteria;
            return List.copyOf(items);
        }
        public void save(StockItem item) {
            items.removeIf(existing -> existing.id().equals(item.id()));
            items.add(item);
        }
    }
}
