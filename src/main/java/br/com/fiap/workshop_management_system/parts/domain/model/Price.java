package br.com.fiap.workshop_management_system.parts.domain.model;

import java.math.BigDecimal;

public record Price(BigDecimal value) {

    public Price {
        if (value == null || value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price value must be >= 0");
        }
    }
}
