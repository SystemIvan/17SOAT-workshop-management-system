package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model;

public class PurchaseOrderTransitionException extends RuntimeException {

    public PurchaseOrderTransitionException(String message) {
        super(message);
    }
}
