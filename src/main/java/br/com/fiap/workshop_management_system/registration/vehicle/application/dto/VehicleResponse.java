package br.com.fiap.workshop_management_system.registration.vehicle.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record VehicleResponse(
        UUID id,
        UUID customerId,
        @Schema(example = "ABC1D23") String licensePlate,
        @Schema(nullable = true, example = "9BWZZZ377VT004251") String chassis,
        String brand,
        String model,
        int year,
        String color,
        @Schema(nullable = true, types = {"integer", "null"}, format = "int64", example = "42500") Long mileage,
        boolean active) {
}
