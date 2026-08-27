package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception;

public class StockReceiptInconsistentException extends RuntimeException {

    public StockReceiptInconsistentException() {
        super("Purchase order references stock items that cannot be received");
    }
}
