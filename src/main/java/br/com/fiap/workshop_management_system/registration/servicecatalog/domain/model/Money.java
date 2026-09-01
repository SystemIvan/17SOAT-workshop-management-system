package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record Money(BigDecimal value, CurrencyCode currency) {

    public Money {
        if (value == null) {
            throw new IllegalArgumentException("O valor do preço-base é obrigatório");
        }
        if (currency == null) {
            throw new IllegalArgumentException("A moeda do preço-base é obrigatória");
        }
        if (currency != CurrencyCode.BRL) {
            throw new IllegalArgumentException("A moeda do preço-base deve ser BRL");
        }

        try {
            value = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("O preço-base deve ter no máximo duas casas decimais", exception);
        }

        if (value.signum() < 0) {
            throw new IllegalArgumentException("O preço-base não pode ser negativo");
        }
        if (value.precision() > 19) {
            throw new IllegalArgumentException("O preço-base deve ter no máximo 17 dígitos inteiros");
        }
    }
}
