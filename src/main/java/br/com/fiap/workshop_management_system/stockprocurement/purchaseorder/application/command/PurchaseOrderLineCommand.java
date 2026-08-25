package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command;

import java.util.UUID;

public record PurchaseOrderLineCommand(UUID stockItemId, int quantity) {

    public PurchaseOrderLineCommand {
        if (stockItemId == null) {
            throw new IllegalArgumentException("Purchase order stock item id must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Purchase order quantity must be greater than zero");
        }
    }
}
