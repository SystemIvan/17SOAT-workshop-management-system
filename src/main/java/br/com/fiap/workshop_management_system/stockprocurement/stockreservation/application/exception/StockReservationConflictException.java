package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception;

public class StockReservationConflictException extends RuntimeException {

    public StockReservationConflictException(String message) {
        super(message);
    }
}
