package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

import java.util.Locale;

public record Sku(String value) {

    public Sku {
        if (value == null) {
            throw new IllegalArgumentException("Stock item SKU must not be null");
        }
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 100) {
            throw new IllegalArgumentException("Stock item SKU must contain between 1 and 100 characters");
        }
    }
}
