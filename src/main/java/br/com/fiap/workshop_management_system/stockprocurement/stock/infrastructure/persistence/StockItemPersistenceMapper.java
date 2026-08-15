package br.com.fiap.workshop_management_system.stockprocurement.stock.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import org.springframework.stereotype.Component;

@Component
public class StockItemPersistenceMapper {
    public StockItemJpaEntity toEntity(StockItem item) {
        return new StockItemJpaEntity(item.id(), item.sku().value(), item.name(), item.type(), item.price().value(),
                item.price().currency().name(), item.availableQuantity().value(), item.active());
    }

    public StockItem toDomain(StockItemJpaEntity entity) {
        return StockItem.reconstitute(entity.getId(), new Sku(entity.getSku()), entity.getName(), entity.getType(),
                new Price(entity.getPriceValue(), CurrencyCode.valueOf(entity.getPriceCurrency())),
                new Quantity(entity.getAvailableQuantity()), entity.isActive());
    }
}
