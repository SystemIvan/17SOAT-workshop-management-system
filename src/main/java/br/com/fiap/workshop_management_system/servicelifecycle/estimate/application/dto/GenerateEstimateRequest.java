package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GenerateEstimateRequest(
        @NotNull UUID diagnosisId
) {
}