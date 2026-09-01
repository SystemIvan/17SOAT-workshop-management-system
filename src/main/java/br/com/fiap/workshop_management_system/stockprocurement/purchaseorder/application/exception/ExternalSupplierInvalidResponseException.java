package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class ExternalSupplierInvalidResponseException extends RuntimeException {

    public ExternalSupplierInvalidResponseException(String message) {
        super(message);
    }

    public ExternalSupplierInvalidResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
