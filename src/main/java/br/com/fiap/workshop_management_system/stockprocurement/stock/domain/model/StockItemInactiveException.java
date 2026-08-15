package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

public class StockItemInactiveException extends IllegalStateException {
    public StockItemInactiveException() {
        super("Stock item is inactive");
    }
}
