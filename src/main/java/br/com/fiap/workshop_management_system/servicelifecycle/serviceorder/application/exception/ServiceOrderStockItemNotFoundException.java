package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

/**
 * Translates {@code stockprocurement}'s {@code StockItemNotFoundException} at the module boundary (AD-011)
 * so it is mapped by {@link br.com.fiap.workshop_management_system.servicelifecycle.ServiceLifecycleExceptionHandler}
 * instead of propagating unhandled and being reported as an unrelated authentication failure.
 */
public class ServiceOrderStockItemNotFoundException extends RuntimeException {

    public ServiceOrderStockItemNotFoundException() {
        super("Stock item was not found");
    }
}
