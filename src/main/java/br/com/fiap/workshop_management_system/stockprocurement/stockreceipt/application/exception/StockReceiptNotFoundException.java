package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception;

public class StockReceiptNotFoundException extends RuntimeException {

    public StockReceiptNotFoundException() {
        super("Stock receipt not found");
    }
}
