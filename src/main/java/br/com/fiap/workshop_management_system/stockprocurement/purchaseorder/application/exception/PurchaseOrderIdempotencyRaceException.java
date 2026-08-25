package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception;

public class PurchaseOrderIdempotencyRaceException extends RuntimeException {

    public PurchaseOrderIdempotencyRaceException(Throwable cause) {
        super("Concurrent purchase order idempotency race", cause);
    }
}
