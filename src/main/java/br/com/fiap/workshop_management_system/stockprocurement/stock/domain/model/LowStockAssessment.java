package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

public record LowStockAssessment(LowStockStatus status, Quantity suggestedPurchaseQuantity) {

    public LowStockAssessment {
        if (status == null) {
            throw new IllegalArgumentException("Low stock status must not be null");
        }
        if ((status == LowStockStatus.LOW) != (suggestedPurchaseQuantity != null)) {
            throw new IllegalArgumentException("Suggested quantity must be present only for low stock");
        }
        if (suggestedPurchaseQuantity != null && suggestedPurchaseQuantity.value() <= 0) {
            throw new IllegalArgumentException("Suggested quantity must be positive");
        }
    }

    public static LowStockAssessment notConfigured() {
        return new LowStockAssessment(LowStockStatus.NOT_CONFIGURED, null);
    }

    public static LowStockAssessment normal() {
        return new LowStockAssessment(LowStockStatus.NORMAL, null);
    }

    public static LowStockAssessment low(Quantity suggestedPurchaseQuantity) {
        return new LowStockAssessment(LowStockStatus.LOW, suggestedPurchaseQuantity);
    }
}
