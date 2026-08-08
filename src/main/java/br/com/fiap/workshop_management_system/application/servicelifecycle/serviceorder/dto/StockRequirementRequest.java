package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.StockItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record StockRequirementRequest(
        @NotNull UUID stockItemId,
        @NotNull StockItemType type,
        @Positive int quantity,
        @NotBlank String nameSnapshot,
        @NotNull @Valid MoneyDTO priceSnapshot) {
}
