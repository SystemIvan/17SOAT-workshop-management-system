package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;

import java.util.UUID;

public record PurchaseDemandStockItemResponse(
        UUID id,
        String sku,
        String name,
        StockItemType type) {
}
