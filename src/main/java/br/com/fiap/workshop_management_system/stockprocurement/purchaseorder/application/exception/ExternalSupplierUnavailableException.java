package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class ExternalSupplierUnavailableException extends RuntimeException {

    public ExternalSupplierUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalSupplierUnavailableException(String message) {
        super(message);
    }
}
