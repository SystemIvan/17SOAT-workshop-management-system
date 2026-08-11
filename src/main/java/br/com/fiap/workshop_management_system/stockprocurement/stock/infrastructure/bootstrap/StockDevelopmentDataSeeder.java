package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Part;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.PartRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
class StockDevelopmentDataSeeder implements ApplicationRunner {

    private static final String SKU = "DEV-OIL-FILTER-001";

    private final PartRepository partRepository;

    StockDevelopmentDataSeeder(PartRepository partRepository) {
        this.partRepository = partRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean alreadySeeded = partRepository.findAll().stream()
                .anyMatch(part -> SKU.equals(part.sku()));

        if (!alreadySeeded) {
            Part part = Part.create(
                    "Development Oil Filter",
                    SKU,
                    20,
                    new Price(new BigDecimal("45.90")));
            partRepository.save(part);
        }
    }
}
