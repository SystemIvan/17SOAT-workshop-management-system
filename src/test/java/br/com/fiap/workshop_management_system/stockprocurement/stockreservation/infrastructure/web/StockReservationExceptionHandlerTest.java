package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.web;

import br.com.fiap.workshop_management_system.ErrorResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationConflictException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.exception.StockReservationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class StockReservationExceptionHandlerTest {

    private final StockReservationExceptionHandler handler = new StockReservationExceptionHandler();

    @Test
    void mapsNotFoundToStableCode() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(new StockReservationNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("STOCK_RESERVATION_NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Stock reservation not found");
    }

    @Test
    void mapsConflictToStableCode() {
        ResponseEntity<ErrorResponse> response =
                handler.handleConflict(new StockReservationConflictException("conflicting origin"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("STOCK_RESERVATION_CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Stock reservation conflicts with its origin");
    }

    @Test
    void mapsInvalidReservationToBadRequest() {
        ResponseEntity<ErrorResponse> response =
                handler.handleInvalidReservation(new IllegalArgumentException("invalid data"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_STOCK_RESERVATION");
        assertThat(response.getBody().message()).isEqualTo("Invalid stock reservation");
    }
}
