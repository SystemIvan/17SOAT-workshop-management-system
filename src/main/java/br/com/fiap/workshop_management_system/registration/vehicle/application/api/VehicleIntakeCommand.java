package br.com.fiap.workshop_management_system.registration.vehicle.application.api;

import java.util.UUID;

/**
 * Vehicle details are only used when no Vehicle exists for the plate; they never update an existing one.
 */
public record VehicleIntakeCommand(
        UUID customerId,
        String licensePlate,
        String chassis,
        String brand,
        String model,
        Integer year,
        String color,
        Long mileage) {
}
