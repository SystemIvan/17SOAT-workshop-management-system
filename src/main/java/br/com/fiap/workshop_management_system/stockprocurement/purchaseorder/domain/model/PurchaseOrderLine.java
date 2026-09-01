package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;

import java.util.Locale;
import java.util.UUID;

public record PurchaseOrderLine(
        UUID stockItemId,
        String skuSnapshot,
        String nameSnapshot,
        StockItemType typeSnapshot,
        int quantity) {

    public PurchaseOrderLine {
        if (stockItemId == null || typeSnapshot == null) {
            throw new IllegalArgumentException("Purchase order line required data must not be null");
        }
        skuSnapshot = normalizeSku(skuSnapshot);
        nameSnapshot = normalizeName(nameSnapshot);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Purchase order line quantity must be greater than zero");
        }
    }

    private static String normalizeSku(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Purchase order line SKU must not be null");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new IllegalArgumentException("Purchase order line SKU must contain between 1 and 100 characters");
        }
        return normalized;
    }

    private static String normalizeName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Purchase order line name must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 255) {
            throw new IllegalArgumentException("Purchase order line name must contain between 1 and 255 characters");
        }
        return normalized;
    }
}
