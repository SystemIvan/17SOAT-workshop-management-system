package br.com.fiap.workshop_management_system.registration.vehicle.application.api;

import java.util.UUID;

/**
 * Vehicle data comes from the persisted registration, normalized by the domain. Every field except
 * {@code outcome} is {@code null} when the Vehicle cannot receive the new work.
 */
public record VehicleIntakeResult(
        UUID vehicleId,
        VehicleIntakeOutcome outcome,
        String licensePlate,
        String brand,
        String model,
        Integer year) {

    public static VehicleIntakeResult rejected(VehicleIntakeOutcome outcome) {
        return new VehicleIntakeResult(null, outcome, null, null, null, null);
    }
}
