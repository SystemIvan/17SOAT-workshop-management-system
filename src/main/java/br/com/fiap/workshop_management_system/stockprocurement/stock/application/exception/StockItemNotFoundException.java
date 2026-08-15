package br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception;

public class StockItemNotFoundException extends RuntimeException {
    public StockItemNotFoundException() {
        super("Stock item was not found");
    }
}
