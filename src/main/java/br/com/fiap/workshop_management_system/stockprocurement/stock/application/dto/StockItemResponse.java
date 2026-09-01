package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;

import java.util.UUID;

public record StockItemResponse(UUID id, String sku, String name, StockItemType type, PriceDto price,
                                int availableQuantity, boolean active, LowStockPolicyResponse lowStockPolicy,
                                LowStockStatusResponse lowStockStatus, UUID lowStockOccurrenceId,
                                Integer suggestedPurchaseQuantity) {

    public StockItemResponse(UUID id, String sku, String name, StockItemType type, PriceDto price,
                             int availableQuantity, boolean active) {
        this(id, sku, name, type, price, availableQuantity, active, null, LowStockStatusResponse.NOT_CONFIGURED,
                null, null);
    }
}
