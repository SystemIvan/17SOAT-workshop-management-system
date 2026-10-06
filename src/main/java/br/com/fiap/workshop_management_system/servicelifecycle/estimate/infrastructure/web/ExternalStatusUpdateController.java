package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalStatusUpdateRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.ApplyExternalStatusUpdateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * RF40 - inbound channel for the customer's decision extracted from an e-mail by an external tool. Reachable
 * only by the HMAC-authenticated gateway (RF41, SecurityConfig).
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Service Orders", description = "Service lifecycle operations")
public class ExternalStatusUpdateController {

    private final ApplyExternalStatusUpdateUseCase applyExternalStatusUpdateUseCase;

    public ExternalStatusUpdateController(ApplyExternalStatusUpdateUseCase applyExternalStatusUpdateUseCase) {
        this.applyExternalStatusUpdateUseCase = applyExternalStatusUpdateUseCase;
    }

    @PostMapping("/service-orders/{serviceOrderId}/external-status-updates")
    @Operation(
            summary = "Apply the customer's decision received through an external channel (e-mail)",
            description = "For the external customer approval gateway only; internal users decide through "
                    + "POST /api/estimates/{estimateId}/decisions. intendedStatus APPROVED or REJECTED is applied "
                    + "to every PENDING line of the service order's SENT estimate, with the same rules as that "
                    + "endpoint. Authentication is an HMAC signature without JWT: X-Estimate-Gateway-Signature is "
                    + "the lowercase hex HMAC-SHA256 of timestamp + \".\" + raw request body, keyed with the shared "
                    + "gateway secret. Signatures older or newer than 300 seconds, and signed bodies over 64 KiB, "
                    + "are rejected with 401.")
    @Parameter(in = ParameterIn.HEADER, name = "X-Estimate-Gateway-Timestamp", required = true,
            description = "Signing instant in UTC epoch seconds",
            schema = @Schema(type = "string", example = "1790434800"))
    @Parameter(in = ParameterIn.HEADER, name = "X-Estimate-Gateway-Signature", required = true,
            description = "Hex HMAC-SHA256(timestamp + \".\" + rawBody, secret)",
            schema = @Schema(type = "string"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Decision applied to every pending line"),
            @ApiResponse(responseCode = "400", description = "intendedStatus missing or not APPROVED/REJECTED"),
            @ApiResponse(responseCode = "401",
                    description = "Missing, wrong or expired gateway signature, or signed body too large"),
            @ApiResponse(responseCode = "403",
                    description = "Authenticated with a JWT instead of the gateway signature"),
            @ApiResponse(responseCode = "404", description = "Service order not found"),
            @ApiResponse(responseCode = "409", description = "No single sent estimate with pending lines for the "
                    + "service order (not yet estimated, already decided, expired, completed or delivered)")
    })
    public ResponseEntity<ServiceOrderResponse> update(
            @PathVariable UUID serviceOrderId, @Valid @RequestBody ExternalStatusUpdateRequest request) {
        return ResponseEntity.ok(applyExternalStatusUpdateUseCase.execute(serviceOrderId, request));
    }
}
