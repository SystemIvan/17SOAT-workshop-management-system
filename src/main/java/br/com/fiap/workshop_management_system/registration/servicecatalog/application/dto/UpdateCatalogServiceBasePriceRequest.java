package br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateCatalogServiceBasePriceRequest(
        @NotNull @Valid MoneyDto basePrice) {
}
