package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;

import java.util.UUID;

public record StockReceiptLineResponse(
        UUID movementId,
        UUID stockItemId,
        String skuSnapshot,
        String nameSnapshot,
        StockItemType typeSnapshot,
        int quantity,
        int availableBefore,
        int availableAfter) {
}
