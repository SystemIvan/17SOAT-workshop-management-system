package br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model;

import java.math.BigDecimal;

public record Money(BigDecimal value, String currency) {

    public Money {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Money value must be >= 0");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Money currency must not be blank");
        }
    }

    public static Money brl(BigDecimal value) {
        return new Money(value, "BRL");
    }
}
