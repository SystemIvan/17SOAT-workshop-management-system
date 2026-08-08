package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.StockItemType;

import java.util.UUID;

public record StockRequirementResponse(
        UUID stockItemId,
        StockItemType type,
        int quantity,
        String nameSnapshot,
        MoneyDTO priceSnapshot,
        boolean reserved) {
}
