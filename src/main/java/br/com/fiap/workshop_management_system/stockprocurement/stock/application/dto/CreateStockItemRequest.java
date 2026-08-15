package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateStockItemRequest(
        @NotBlank @Size(max = 100) String sku,
        @NotBlank @Size(max = 255) String name,
        @NotNull StockItemType type,
        @NotNull @Valid PriceDto price,
        @NotNull @PositiveOrZero Integer availableQuantity) {
}
