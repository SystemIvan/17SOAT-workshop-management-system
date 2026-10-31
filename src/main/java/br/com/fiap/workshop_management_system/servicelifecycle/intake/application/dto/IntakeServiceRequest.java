package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record IntakeServiceRequest(
        @NotNull UUID catalogServiceId,
        @Size(max = 20, message = "Cada serviço aceita no máximo 20 peças")
        @Valid
        List<@NotNull(message = "A peça não pode ser nula") IntakePartRequest> parts) {
}
