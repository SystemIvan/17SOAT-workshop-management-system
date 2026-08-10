package br.com.fiap.workshop_management_system.parts.application.dto;

import jakarta.validation.constraints.Positive;

public record AdjustPartStockRequest(@Positive int amount) {
}
