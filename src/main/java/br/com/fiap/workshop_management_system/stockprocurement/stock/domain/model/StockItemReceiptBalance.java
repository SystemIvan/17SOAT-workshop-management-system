package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

public record StockItemReceiptBalance(Quantity availableBefore, Quantity availableAfter) {

    public StockItemReceiptBalance {
        if (availableBefore == null || availableAfter == null || availableAfter.value() < availableBefore.value()) {
            throw new IllegalArgumentException("Stock receipt balances are inconsistent");
        }
    }
}
