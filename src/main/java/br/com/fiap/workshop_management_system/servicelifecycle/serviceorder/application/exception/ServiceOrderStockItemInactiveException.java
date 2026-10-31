package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception;

public class ServiceOrderStockItemInactiveException extends RuntimeException {

    public ServiceOrderStockItemInactiveException() {
        super("Stock item is inactive");
    }
}
