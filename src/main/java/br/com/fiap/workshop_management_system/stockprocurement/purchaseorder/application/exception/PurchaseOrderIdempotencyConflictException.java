package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class PurchaseOrderIdempotencyConflictException extends RuntimeException {

    public PurchaseOrderIdempotencyConflictException() {
        super("Idempotency key is already associated with a different purchase order command");
    }
}
