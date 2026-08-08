package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PerformDiagnosisRequest(
        @NotEmpty @Valid List<DiagnosisItemRequest> items) {
}
