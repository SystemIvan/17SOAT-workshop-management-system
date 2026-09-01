package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception;

public class StockQuantityOverflowException extends RuntimeException {

    public StockQuantityOverflowException(ArithmeticException cause) {
        super("Stock quantity exceeds the supported range", cause);
    }
}
