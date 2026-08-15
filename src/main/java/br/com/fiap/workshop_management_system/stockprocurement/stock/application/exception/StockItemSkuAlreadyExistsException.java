package br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception;

public class StockItemSkuAlreadyExistsException extends RuntimeException {
    public StockItemSkuAlreadyExistsException() {
        super("A stock item with this SKU already exists");
    }
}
