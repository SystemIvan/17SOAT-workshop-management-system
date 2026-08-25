package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

public interface PurchaseDemandApi {

    void recordLowStock(LowStockPurchaseDemandCommand command);
}
