package br.com.fiap.workshop_management_system.application.parts.dto;

import jakarta.validation.constraints.Positive;

public record AdjustPartStockRequest(@Positive int amount) {
}
