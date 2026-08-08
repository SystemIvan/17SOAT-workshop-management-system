package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.Priority;
import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.ServiceOrderStatus;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ServiceOrderResponse(
        UUID id,
        UUID customerId,
        UUID vehicleId,
        VehicleSnapshotResponse vehicleSnapshot,
        Priority priority,
        ServiceOrderStatus status,
        Set<UUID> approvedEstimateIds,
        List<ServiceExecutionResponse> executions) {
}
