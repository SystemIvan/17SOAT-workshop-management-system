package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record DiagnosisItemRequest(
        @NotNull UUID catalogServiceId,
        @NotBlank String name,
        @NotNull @Valid MoneyDTO price,
        @Valid List<StockRequirementRequest> stockRequirements) {
}
