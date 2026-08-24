package br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto;

import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record MoneyDto(
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal value,
        @NotNull CurrencyCode currency) {
}
