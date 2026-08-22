package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationConflictException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = StockReservationController.class)
class StockReservationExceptionHandler {

    @ExceptionHandler(StockReservationNotFoundException.class)
    ResponseEntity<ErrorResponse> handleNotFound(StockReservationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("STOCK_RESERVATION_NOT_FOUND", "Stock reservation not found"));
    }

    @ExceptionHandler(StockReservationConflictException.class)
    ResponseEntity<ErrorResponse> handleConflict(StockReservationConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("STOCK_RESERVATION_CONFLICT", "Stock reservation conflicts with its origin"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorResponse> handleInvalidReservation(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_STOCK_RESERVATION", "Invalid stock reservation"));
    }
}
