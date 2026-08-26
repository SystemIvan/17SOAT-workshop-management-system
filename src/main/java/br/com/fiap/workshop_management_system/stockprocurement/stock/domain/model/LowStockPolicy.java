package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

public record LowStockPolicy(Quantity minimumQuantity, Quantity targetQuantity) {

    public LowStockPolicy {
        if (minimumQuantity == null || targetQuantity == null) {
            throw new InvalidLowStockPolicyException("Minimum and target quantities must be provided together");
        }
        if (targetQuantity.value() <= minimumQuantity.value()) {
            throw new InvalidLowStockPolicyException("Target quantity must be greater than minimum quantity");
        }
    }

    public boolean isLow(Quantity availableQuantity) {
        requireAvailableQuantity(availableQuantity);
        return availableQuantity.value() < minimumQuantity.value();
    }

    public Quantity suggestedPurchase(Quantity availableQuantity) {
        requireAvailableQuantity(availableQuantity);
        if (!isLow(availableQuantity)) {
            throw new InvalidLowStockPolicyException("Suggested purchase requires a low stock condition");
        }
        return new Quantity(Math.subtractExact(targetQuantity.value(), availableQuantity.value()));
    }

    private static void requireAvailableQuantity(Quantity availableQuantity) {
        if (availableQuantity == null) {
            throw new InvalidLowStockPolicyException("Available quantity must not be null");
        }
    }
}
