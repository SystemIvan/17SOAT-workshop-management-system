package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

public record Quantity(int value) {

    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("Quantity must be >= 0");
        }
    }

}
