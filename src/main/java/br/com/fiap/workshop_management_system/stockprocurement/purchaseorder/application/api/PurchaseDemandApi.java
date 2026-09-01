package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

public interface PurchaseDemandApi {

    LowStockPurchaseDemandResult recordLowStock(LowStockPurchaseDemandCommand command);

    void resolveLowStock(LowStockPurchaseDemandResolutionCommand command);
}
