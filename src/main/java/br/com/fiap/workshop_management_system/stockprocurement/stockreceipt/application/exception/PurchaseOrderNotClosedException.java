package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception;

public class PurchaseOrderNotClosedException extends RuntimeException {

    public PurchaseOrderNotClosedException() {
        super("Purchase order must be closed before receiving it");
    }
}
