package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Price;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrence;

public final class StockItemMapper {
    private StockItemMapper() {
    }

    public static StockItemResponse toResponse(StockItem item) {
        return toResponse(item, null);
    }

    public static StockItemResponse toResponse(StockItem item, LowStockOccurrence occurrence) {
        var assessment = item.assessLowStock();
        LowStockPolicyResponse policy = item.lowStockPolicy() == null ? null : new LowStockPolicyResponse(
                item.lowStockPolicy().minimumQuantity().value(), item.lowStockPolicy().targetQuantity().value());
        boolean low = assessment.status() == br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model
                .LowStockStatus.LOW;
        return new StockItemResponse(item.id(), item.sku().value(), item.name(), item.type(), toDto(item.price()),
                item.availableQuantity().value(), item.active(), policy,
                LowStockStatusResponse.valueOf(assessment.status().name()), low && occurrence != null ? occurrence.id() : null,
                low ? assessment.suggestedPurchaseQuantity().value() : null);
    }

    public static Price toPrice(PriceDto dto) {
        return new Price(dto.value(), dto.currency());
    }

    public static PriceDto toDto(Price price) {
        return new PriceDto(price.value(), price.currency());
    }
}
