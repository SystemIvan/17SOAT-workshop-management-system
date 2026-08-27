package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.util.UUID;

public record LowStockPurchaseDemandResult(UUID purchaseDemandId, PurchaseDemandStatusView status) {

    public LowStockPurchaseDemandResult {
        if (purchaseDemandId == null || status == null) {
            throw new IllegalArgumentException("Low stock purchase demand result must be complete");
        }
    }
}
