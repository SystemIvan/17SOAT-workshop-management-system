package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception;

public class StockReservationNotFoundException extends RuntimeException {

    public StockReservationNotFoundException() {
        super("Stock reservation not found");
    }
}
