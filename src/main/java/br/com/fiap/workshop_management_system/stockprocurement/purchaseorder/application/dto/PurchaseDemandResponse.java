package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;

import java.time.Instant;
import java.util.UUID;

public record PurchaseDemandResponse(
        UUID id,
        PurchaseDemandOrigin origin,
        PurchaseDemandStockItemResponse stockItem,
        Integer requestedQuantity,
        int observedAvailableQuantity,
        int suggestedQuantity,
        UUID serviceExecutionId,
        Instant createdAt) {
}
