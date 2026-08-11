package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record VehicleSnapshotRequest(
        @NotBlank String licensePlate,
        @NotBlank String brand,
        @NotBlank String model,
        @Positive int year) {
}
