package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import java.util.UUID;

public record PartResponse(
        UUID id,
        String name,
        String sku,
        int quantity,
        PriceDTO price) {
}
