package br.com.fiap.workshop_management_system.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrderStatus;

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
