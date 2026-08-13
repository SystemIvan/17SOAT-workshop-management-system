package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Part;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.PartRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StockDevelopmentDataSeederTest {

    @Test
    void seedOnlyOnceWhenRunRepeatedly() {
        InMemoryPartRepository repository = new InMemoryPartRepository();
        StockDevelopmentDataSeeder seeder = new StockDevelopmentDataSeeder(repository);

        seeder.run(null);
        seeder.run(null);

        assertEquals(1, repository.parts.size());
        assertEquals("DEV-OIL-FILTER-001", repository.parts.getFirst().sku());
    }

    private static final class InMemoryPartRepository implements PartRepository {

        private final List<Part> parts = new ArrayList<>();

        @Override
        public Optional<Part> findById(UUID id) {
            return parts.stream().filter(part -> part.id().equals(id)).findFirst();
        }

        @Override
        public List<Part> findAll() {
            return List.copyOf(parts);
        }

        @Override
        public void save(Part part) {
            parts.add(part);
        }
    }
}
