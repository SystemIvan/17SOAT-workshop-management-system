package br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto;

import java.util.UUID;

public record CatalogServiceResponse(
        UUID id,
        String name,
        MoneyDto basePrice,
        boolean active) {
}
