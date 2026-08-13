package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreatePartRequest(
        @NotBlank String name,
        @NotBlank String sku,
        @PositiveOrZero int initialQuantity,
        @NotNull @Valid PriceDTO price) {
}
