package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceExecutionStatus;

import java.util.List;
import java.util.UUID;

public record ServiceExecutionResponse(
        UUID id,
        UUID diagnosisId,
        UUID catalogServiceId,
        String name,
        MoneyDTO price,
        ServiceExecutionStatus status,
        UUID authorizedByEstimateId,
        UUID assignedTechnicianId,
        List<StockRequirementResponse> stockRequirements) {
}
