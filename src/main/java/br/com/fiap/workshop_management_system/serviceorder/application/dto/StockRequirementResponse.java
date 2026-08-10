package br.com.fiap.workshop_management_system.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.serviceorder.domain.model.StockItemType;

import java.util.UUID;

public record StockRequirementResponse(
        UUID stockItemId,
        StockItemType type,
        int quantity,
        String nameSnapshot,
        MoneyDTO priceSnapshot,
        boolean reserved) {
}
