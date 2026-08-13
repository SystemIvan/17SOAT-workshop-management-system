package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import jakarta.validation.constraints.Positive;

public record AdjustPartStockRequest(@Positive int amount) {
}
