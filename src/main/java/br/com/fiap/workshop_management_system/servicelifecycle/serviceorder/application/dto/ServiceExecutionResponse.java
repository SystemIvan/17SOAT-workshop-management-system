package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;

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
        UUID stockReservationId,
        List<StockRequirementResponse> stockRequirements) {
}
