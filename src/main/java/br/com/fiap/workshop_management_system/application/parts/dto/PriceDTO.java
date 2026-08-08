package br.com.fiap.workshop_management_system.application.parts.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record PriceDTO(@NotNull @PositiveOrZero BigDecimal value) {
}
