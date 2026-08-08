package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateServiceOrderRequest(
        @NotNull UUID customerId,
        @NotNull UUID vehicleId,
        @NotNull @Valid VehicleSnapshotRequest vehicleSnapshot,
        Priority priority) {
}
