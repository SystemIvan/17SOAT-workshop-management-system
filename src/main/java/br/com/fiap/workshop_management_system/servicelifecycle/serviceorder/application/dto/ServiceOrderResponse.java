package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ServiceOrderResponse(
        UUID id,
        UUID customerId,
        UUID vehicleId,
        VehicleSnapshotResponse vehicleSnapshot,
        Priority priority,
        @Schema(nullable = true) String initialAssessment,
        @Schema(nullable = true) UUID diagnosisAssigneeId,
        @Schema(deprecated = true) ServiceOrderStatus status,
        ServiceOrderStatus statusSnapshot,
        Set<UUID> approvedEstimateIds,
        List<ServiceExecutionResponse> executions) {
}
