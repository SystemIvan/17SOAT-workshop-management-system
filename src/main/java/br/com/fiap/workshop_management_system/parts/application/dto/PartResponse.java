package br.com.fiap.workshop_management_system.parts.application.dto;

import java.util.UUID;

public record PartResponse(
        UUID id,
        String name,
        String sku,
        int quantity,
        PriceDTO price) {
}
