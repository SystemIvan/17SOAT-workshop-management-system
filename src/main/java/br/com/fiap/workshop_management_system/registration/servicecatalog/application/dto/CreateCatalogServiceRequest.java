package br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCatalogServiceRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Valid MoneyDto basePrice) {
}
