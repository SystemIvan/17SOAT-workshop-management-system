package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class PurchaseDemandNotFoundException extends RuntimeException {

    public PurchaseDemandNotFoundException() {
        super("Purchase demand was not found");
    }
}
