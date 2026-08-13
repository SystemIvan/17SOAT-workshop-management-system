package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

import java.util.UUID;

/**
 * Aggregate Root. The only entry point for changing a Part's data or stock.
 */
public class Part {

    private final UUID id;
    private final String sku;

    private String name;
    private Quantity quantity;
    private Price price;

    public static Part create(String name, String sku, int initialQuantity, Price price) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Part name must not be blank");
        }
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("Part sku must not be blank");
        }
        if (price == null) {
            throw new IllegalArgumentException("Part price must not be null");
        }
        return new Part(UUID.randomUUID(), name, sku, new Quantity(initialQuantity), price);
    }

    private Part(UUID id, String name, String sku, Quantity quantity, Price price) {
        this.id = id;
        this.name = name;
        this.sku = sku;
        this.quantity = quantity;
        this.price = price;
    }

    /**
     * Rebuilds a Part from previously persisted state. Used exclusively by the
     * persistence adapter - unlike {@link #create}, it does not run creation rules.
     */
    public static Part reconstitute(UUID id, String name, String sku, Quantity quantity, Price price) {
        return new Part(id, name, sku, quantity, price);
    }

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("Part name must not be blank");
        }
        this.name = newName;
    }

    public void increaseStock(int amount) {
        this.quantity = quantity.increase(amount);
    }

    public void decreaseStock(int amount) {
        this.quantity = quantity.decrease(amount);
    }

    public void changePrice(Price newPrice) {
        if (newPrice == null) {
            throw new IllegalArgumentException("Part price must not be null");
        }
        this.price = newPrice;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String sku() {
        return sku;
    }

    public Quantity quantity() {
        return quantity;
    }

    public Price price() {
        return price;
    }
}
