package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Full intake request. It intentionally has no name, price, type or snapshot fields: those are copied from the
 * registrations, so unknown properties sent by clients never reach the domain.
 */
public record OpenServiceOrderIntakeRequest(
        @NotNull @Valid IntakeCustomerRequest customer,
        @NotNull @Valid IntakeVehicleRequest vehicle,
        @Schema(nullable = true, description = "Defaults to NORMAL") Priority priority,
        @NotBlank String initialAssessment,
        @Schema(nullable = true, description = "Required when services are informed")
        UUID diagnosisTechnicianId,
        @Size(max = 20, message = "A abertura aceita no máximo 20 serviços")
        @Valid
        List<@NotNull(message = "O serviço não pode ser nulo") IntakeServiceRequest> services) {
}
