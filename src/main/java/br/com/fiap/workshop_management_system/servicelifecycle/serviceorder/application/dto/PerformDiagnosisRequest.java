package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PerformDiagnosisRequest(
        @NotNull UUID diagnosedByTechnicianId,
        @NotEmpty @Valid List<DiagnosisItemRequest> items) {
}
