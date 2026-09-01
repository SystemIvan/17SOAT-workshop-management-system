package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Price(BigDecimal value, CurrencyCode currency) {

    public Price {
        if (value == null || currency == null) {
            throw new IllegalArgumentException("Stock item price and currency must not be null");
        }
        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Stock item price must have at most two decimal places", exception);
        }
        if (currency != CurrencyCode.BRL || value.signum() < 0 || value.precision() > 19) {
            throw new IllegalArgumentException(
                    "Stock item price must be a non-negative BRL value with up to 17 integer digits");
        }
    }
}
