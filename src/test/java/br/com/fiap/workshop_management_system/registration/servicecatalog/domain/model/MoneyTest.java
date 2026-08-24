package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void acceptsZeroAndNormalizesScaleWithoutRounding() {
        Money zero = new Money(BigDecimal.ZERO, CurrencyCode.BRL);
        Money positive = new Money(new BigDecimal("150.5"), CurrencyCode.BRL);

        assertThat(zero.value()).isEqualByComparingTo("0.00");
        assertThat(zero.value().scale()).isEqualTo(2);
        assertThat(positive.value()).isEqualByComparingTo("150.50");
        assertThat(positive.value().scale()).isEqualTo(2);
    }

    @Test
    void rejectsNegativeExcessScaleAndExcessPrecision() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"), CurrencyCode.BRL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(new BigDecimal("1.001"), CurrencyCode.BRL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(new BigDecimal("100000000000000000.00"), CurrencyCode.BRL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingValueOrCurrency() {
        assertThatThrownBy(() -> new Money(null, CurrencyCode.BRL))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
