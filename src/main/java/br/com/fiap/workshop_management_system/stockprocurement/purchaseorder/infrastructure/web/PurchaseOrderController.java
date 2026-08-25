package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.web;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.CreatePurchaseOrderCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.PurchaseOrderLineCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.CreatePurchaseOrderResult;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseOrderResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase.CreatePurchaseOrderUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase.GetPurchaseOrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/purchase-orders")
@Tag(name = "Purchase Orders", description = "Purchase Orders confirmed by the external supplier simulator")
public class PurchaseOrderController {

    private final CreatePurchaseOrderUseCase createUseCase;
    private final GetPurchaseOrderUseCase getUseCase;

    public PurchaseOrderController(
            CreatePurchaseOrderUseCase createUseCase,
            GetPurchaseOrderUseCase getUseCase) {
        this.createUseCase = createUseCase;
        this.getUseCase = getUseCase;
    }

    @PostMapping
    @Operation(
            summary = "Create and submit a Purchase Order",
            description = "The same Idempotency-Key and normalized request can be retried safely")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase Order confirmed for the first time"),
            @ApiResponse(responseCode = "200", description = "Existing confirmed Purchase Order replayed"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Manager or administrator role required"),
            @ApiResponse(responseCode = "404", description = "Demand or Stock Item not found"),
            @ApiResponse(responseCode = "409", description = "Demand, Stock Item or idempotency conflict"),
            @ApiResponse(responseCode = "422", description = "External supplier rejected the order"),
            @ApiResponse(responseCode = "502", description = "External supplier returned an invalid response"),
            @ApiResponse(responseCode = "503", description = "External supplier is unavailable")
    })
    public ResponseEntity<PurchaseOrderResponse> create(
            @Parameter(required = true, description = "UUID that identifies this creation command")
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody CreatePurchaseOrderRequest request) {
        CreatePurchaseOrderResult result = createUseCase.execute(idempotencyKey, toCommand(request));
        if (!result.created()) {
            return ResponseEntity.ok(result.purchaseOrder());
        }
        URI location = URI.create("/api/purchase-orders/" + result.purchaseOrder().id());
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, location.toString())
                .body(result.purchaseOrder());
    }

    @GetMapping("/{purchaseOrderId}")
    @Operation(summary = "Get a confirmed OPEN Purchase Order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Purchase Order found"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Manager or administrator role required"),
            @ApiResponse(responseCode = "404", description = "OPEN Purchase Order not found")
    })
    public ResponseEntity<PurchaseOrderResponse> get(@PathVariable UUID purchaseOrderId) {
        return ResponseEntity.ok(getUseCase.execute(purchaseOrderId));
    }

    private CreatePurchaseOrderCommand toCommand(CreatePurchaseOrderRequest request) {
        List<UUID> demandIds = request.demandIds() == null ? List.of() : request.demandIds();
        List<PurchaseOrderLineCommand> lines = request.lines().stream()
                .map(line -> new PurchaseOrderLineCommand(line.stockItemId(), line.quantity()))
                .toList();
        return new CreatePurchaseOrderCommand(demandIds, lines);
    }
}
