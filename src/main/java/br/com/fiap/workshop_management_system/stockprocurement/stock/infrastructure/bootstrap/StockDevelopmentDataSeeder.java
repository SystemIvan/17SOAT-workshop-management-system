package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
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

    private final StockItemRepository repository;

    StockDevelopmentDataSeeder(StockItemRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!repository.existsBySku(new Sku(SKU))) {
            StockItem item = StockItem.create(
                    new Sku(SKU),
                    "Development Oil Filter",
                    StockItemType.PART,
                    new Price(new BigDecimal("45.90"), CurrencyCode.BRL),
                    new Quantity(20));
            repository.save(item);
        }
    }
}
