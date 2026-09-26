package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.web;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalStatusUpdateRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.ApplyExternalStatusUpdateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
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
public class ExternalStatusUpdateController {

    private final ApplyExternalStatusUpdateUseCase applyExternalStatusUpdateUseCase;

    public ExternalStatusUpdateController(ApplyExternalStatusUpdateUseCase applyExternalStatusUpdateUseCase) {
        this.applyExternalStatusUpdateUseCase = applyExternalStatusUpdateUseCase;
    }

    @PostMapping("/service-orders/{serviceOrderId}/external-status-updates")
    public ResponseEntity<ServiceOrderResponse> update(
            @PathVariable UUID serviceOrderId, @Valid @RequestBody ExternalStatusUpdateRequest request) {
        return ResponseEntity.ok(applyExternalStatusUpdateUseCase.execute(serviceOrderId, request));
    }
}
