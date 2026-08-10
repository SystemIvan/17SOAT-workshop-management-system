package br.com.fiap.workshop_management_system.serviceorder.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PerformDiagnosisRequest(
        @NotEmpty @Valid List<DiagnosisItemRequest> items) {
}
