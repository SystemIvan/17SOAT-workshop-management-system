package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockDevelopmentDataSeederTest {
    @Test
    void seedsOnlyOnceByNormalizedSku() {
        InMemoryRepository repository = new InMemoryRepository();
        StockDevelopmentDataSeeder seeder = new StockDevelopmentDataSeeder(repository);
        seeder.run(null);
        seeder.run(null);
        assertEquals(1, repository.items.size());
        assertEquals("DEV-OIL-FILTER-001", repository.items.getFirst().sku().value());
    }

    private static final class InMemoryRepository implements StockItemRepository {
        private final List<StockItem> items = new ArrayList<>();
        public Optional<StockItem> findById(UUID id) {
            return items.stream().filter(item -> item.id().equals(id)).findFirst();
        }
        public boolean existsBySku(Sku sku) { return items.stream().anyMatch(item -> item.sku().equals(sku)); }
        public List<StockItem> search(StockItemSearchCriteria criteria) { return List.copyOf(items); }
        public void save(StockItem item) { items.add(item); }
    }
}
