package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model;

public class PurchaseDemandNotSelectableException extends RuntimeException {

    public PurchaseDemandNotSelectableException() {
        super("Purchase demand is not selectable");
    }
}
