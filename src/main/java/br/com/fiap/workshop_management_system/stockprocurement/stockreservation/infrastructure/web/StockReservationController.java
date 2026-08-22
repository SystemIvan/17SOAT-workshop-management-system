package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.web;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto.StockReservationResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase.ConsumeStockReservationUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase.GetStockReservationByExecutionUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.usecase.GetStockReservationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/stock-reservations")
@Tag(name = "Stock Reservations", description = "Stock reservation queries and consumption")
public class StockReservationController {

    private final GetStockReservationUseCase getStockReservationUseCase;
    private final GetStockReservationByExecutionUseCase getStockReservationByExecutionUseCase;
    private final ConsumeStockReservationUseCase consumeStockReservationUseCase;

    public StockReservationController(
            GetStockReservationUseCase getStockReservationUseCase,
            GetStockReservationByExecutionUseCase getStockReservationByExecutionUseCase,
            ConsumeStockReservationUseCase consumeStockReservationUseCase) {
        this.getStockReservationUseCase = getStockReservationUseCase;
        this.getStockReservationByExecutionUseCase = getStockReservationByExecutionUseCase;
        this.consumeStockReservationUseCase = consumeStockReservationUseCase;
    }

    @GetMapping("/{reservationId}")
    @Operation(summary = "Get a stock reservation by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock reservation found"),
            @ApiResponse(responseCode = "404", description = "Stock reservation not found")
    })
    public ResponseEntity<StockReservationResponse> get(@PathVariable UUID reservationId) {
        return ResponseEntity.ok(getStockReservationUseCase.execute(reservationId));
    }

    @GetMapping("/by-service-execution/{serviceExecutionId}")
    @Operation(summary = "Get a stock reservation by service execution ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock reservation found"),
            @ApiResponse(responseCode = "404", description = "Stock reservation not found")
    })
    public ResponseEntity<StockReservationResponse> getByServiceExecution(
            @PathVariable UUID serviceExecutionId) {
        return ResponseEntity.ok(getStockReservationByExecutionUseCase.execute(serviceExecutionId));
    }

    @PostMapping("/{reservationId}/consume")
    @Operation(summary = "Consume a stock reservation in full")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock reservation consumed"),
            @ApiResponse(responseCode = "404", description = "Stock reservation not found")
    })
    public ResponseEntity<StockReservationResponse> consume(@PathVariable UUID reservationId) {
        return ResponseEntity.ok(consumeStockReservationUseCase.execute(reservationId));
    }
}
