package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ServiceOrderIntakeResponse(
        ServiceOrderResponse serviceOrder,
        RegistrationReference customer,
        RegistrationReference vehicle,
        @Schema(nullable = true, description = "Null when no services were informed") UUID estimateId) {

    /**
     * {@code created} tells whether the registration was created by this intake or an existing one was reused.
     */
    public record RegistrationReference(UUID id, boolean created) {
    }
}
