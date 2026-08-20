package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record DecideEstimateLinesRequest(
        @NotEmpty @Valid List<LineDecisionRequest> decisions) {

    public record LineDecisionRequest(
            @NotNull UUID serviceExecutionId,
            @NotNull EstimateLineDecision decision) {
    }
}
