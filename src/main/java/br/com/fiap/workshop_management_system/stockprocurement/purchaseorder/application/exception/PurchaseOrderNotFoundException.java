package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class PurchaseOrderNotFoundException extends RuntimeException {

    public PurchaseOrderNotFoundException() {
        super("Purchase order was not found");
    }
}
