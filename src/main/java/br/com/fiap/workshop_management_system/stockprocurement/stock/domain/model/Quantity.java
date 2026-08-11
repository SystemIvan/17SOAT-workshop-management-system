package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

public record Quantity(int value) {

    public Quantity {
        if (value < 0) {
            throw new IllegalArgumentException("Quantity must be >= 0");
        }
    }

    public Quantity increase(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Quantity increase amount must be > 0");
        }
        return new Quantity(value + amount);
    }

    public Quantity decrease(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Quantity decrease amount must be > 0");
        }
        if (amount > value) {
            throw new IllegalStateException("Insufficient quantity to decrease by " + amount);
        }
        return new Quantity(value - amount);
    }
}
