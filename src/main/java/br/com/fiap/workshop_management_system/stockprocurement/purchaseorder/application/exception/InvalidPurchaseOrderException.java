package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class InvalidPurchaseOrderException extends RuntimeException {

    public InvalidPurchaseOrderException(String message) {
        super(message);
    }

    public InvalidPurchaseOrderException(String message, Throwable cause) {
        super(message, cause);
    }
}
