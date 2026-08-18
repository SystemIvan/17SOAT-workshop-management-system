package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.CurrencyCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record PriceDto(
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal value,
        @NotNull CurrencyCode currency) {
}
