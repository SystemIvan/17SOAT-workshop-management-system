package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateServiceOrderRequest(
        @NotNull UUID customerId,
        @NotNull UUID vehicleId,
        @NotNull @Valid VehicleSnapshotRequest vehicleSnapshot,
        Priority priority) {
}
