package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;

public final class StockItemMapper {
    private StockItemMapper() {
    }

    public static StockItemResponse toResponse(StockItem item) {
        return new StockItemResponse(item.id(), item.sku().value(), item.name(), item.type(), toDto(item.price()),
                item.availableQuantity().value(), item.active());
    }

    public static Price toPrice(PriceDto dto) {
        return new Price(dto.value(), dto.currency());
    }

    public static PriceDto toDto(Price price) {
        return new PriceDto(price.value(), price.currency());
    }
}
