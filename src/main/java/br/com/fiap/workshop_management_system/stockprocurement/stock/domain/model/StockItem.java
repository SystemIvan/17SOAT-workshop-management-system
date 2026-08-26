package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

import java.util.UUID;

public class StockItem {

    private final UUID id;
    private final Sku sku;
    private final StockItemType type;
    private Quantity availableQuantity;
    private String name;
    private Price price;
    private boolean active;
    private LowStockPolicy lowStockPolicy;

    public static StockItem create(Sku sku, String name, StockItemType type, Price price, Quantity availableQuantity) {
        return create(sku, name, type, price, availableQuantity, null);
    }

    public static StockItem create(Sku sku, String name, StockItemType type, Price price, Quantity availableQuantity,
                                   LowStockPolicy lowStockPolicy) {
        return new StockItem(UUID.randomUUID(), sku, name, type, price, availableQuantity, true, lowStockPolicy);
    }

    public static StockItem reconstitute(UUID id, Sku sku, String name, StockItemType type, Price price,
                                         Quantity availableQuantity, boolean active) {
        return reconstitute(id, sku, name, type, price, availableQuantity, active, null);
    }

    public static StockItem reconstitute(UUID id, Sku sku, String name, StockItemType type, Price price,
                                         Quantity availableQuantity, boolean active, LowStockPolicy lowStockPolicy) {
        return new StockItem(id, sku, name, type, price, availableQuantity, active, lowStockPolicy);
    }

    private StockItem(UUID id, Sku sku, String name, StockItemType type, Price price, Quantity availableQuantity,
                      boolean active, LowStockPolicy lowStockPolicy) {
        if (id == null || sku == null || type == null || price == null || availableQuantity == null) {
            throw new IllegalArgumentException("Stock item required data must not be null");
        }
        this.id = id;
        this.sku = sku;
        this.name = validateName(name);
        this.type = type;
        this.price = price;
        this.availableQuantity = availableQuantity;
        this.active = active;
        this.lowStockPolicy = lowStockPolicy;
    }

    public void updateDetails(String name, Price price) {
        if (!active) {
            throw new StockItemInactiveException();
        }
        if (name != null) {
            this.name = validateName(name);
        }
        if (price != null) {
            this.price = price;
        }
    }

    public void deactivate() {
        active = false;
        lowStockPolicy = null;
    }

    public void configureLowStockPolicy(LowStockPolicy policy) {
        if (!active) {
            throw new StockItemInactiveException();
        }
        if (policy == null) {
            throw new InvalidLowStockPolicyException("Low stock policy must not be null");
        }
        lowStockPolicy = policy;
    }

    public void disableLowStockPolicy() {
        lowStockPolicy = null;
    }

    public LowStockAssessment assessLowStock() {
        if (!active || lowStockPolicy == null) {
            return LowStockAssessment.notConfigured();
        }
        if (!lowStockPolicy.isLow(availableQuantity)) {
            return LowStockAssessment.normal();
        }
        return LowStockAssessment.low(lowStockPolicy.suggestedPurchase(availableQuantity));
    }

    public boolean hasAvailableQuantity() {
        return availableQuantity.value() > 0;
    }

    /**
     * Evaluates a reservation without changing the available balance.
     */
    public StockItemReservationAssessment assessReservation(Quantity requestedQuantity) {
        validateReservationQuantity(requestedQuantity);
        if (!active) {
            return new StockItemReservationAssessment(
                    StockItemReservationEligibility.INACTIVE, availableQuantity);
        }
        if (availableQuantity.value() < requestedQuantity.value()) {
            return new StockItemReservationAssessment(
                    StockItemReservationEligibility.INSUFFICIENT_QUANTITY, availableQuantity);
        }
        return new StockItemReservationAssessment(StockItemReservationEligibility.ELIGIBLE, availableQuantity);
    }

    /**
     * Commits a previously validated reservation without ever making the balance negative.
     */
    public void reserve(Quantity requestedQuantity) {
        StockItemReservationAssessment assessment = assessReservation(requestedQuantity);
        if (!assessment.eligible()) {
            throw new IllegalStateException("Stock item cannot satisfy the requested reservation: "
                    + assessment.eligibility());
        }
        this.availableQuantity = new Quantity(availableQuantity.value() - requestedQuantity.value());
    }

    public StockItemReceiptBalance receive(Quantity receivedQuantity) {
        if (receivedQuantity == null || receivedQuantity.value() <= 0) {
            throw new IllegalArgumentException("Received quantity must be greater than zero");
        }
        Quantity availableBefore = availableQuantity;
        try {
            availableQuantity = new Quantity(Math.addExact(availableQuantity.value(), receivedQuantity.value()));
        } catch (ArithmeticException exception) {
            throw new ArithmeticException("Stock quantity exceeds the supported range");
        }
        return new StockItemReceiptBalance(availableBefore, availableQuantity);
    }

    private static void validateReservationQuantity(Quantity requestedQuantity) {
        if (requestedQuantity == null || requestedQuantity.value() <= 0) {
            throw new IllegalArgumentException("Reserved quantity must be greater than zero");
        }
    }

    private static String validateName(String value) {
        if (value == null || value.isBlank() || value.length() > 255) {
            throw new IllegalArgumentException("Stock item name must contain between 1 and 255 characters");
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public Sku sku() { return sku; }
    public String name() { return name; }
    public StockItemType type() { return type; }
    public Price price() { return price; }
    public Quantity availableQuantity() { return availableQuantity; }
    public boolean active() { return active; }
    public LowStockPolicy lowStockPolicy() { return lowStockPolicy; }
}
