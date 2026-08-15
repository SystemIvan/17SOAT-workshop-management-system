package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStockItemRequest(
        @NotBlank @Size(max = 255) String name,
        @Valid PriceDto price) {
    @AssertTrue(message = "at least one of name or price must be provided")
    public boolean hasDetailsToUpdate() {
        return name != null || price != null;
    }
}
