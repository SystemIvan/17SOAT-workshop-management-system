package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import java.util.UUID;

/**
 * Identifies or registers the Vehicle of a full Service Order intake inside the current transaction.
 *
 * <p>Implementations report invalid registration data with
 * {@link br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
 * .ServiceOrderIntakeInvalidDataException} and a concurrent registration with
 * {@link br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
 * .ServiceOrderIntakeConcurrentRegistrationException}.
 */
public interface VehicleIntakePort {

    VehicleIntake identifyOrRegister(VehicleData data);

    record VehicleData(
            UUID customerId,
            String licensePlate,
            String chassis,
            String brand,
            String model,
            Integer year,
            String color,
            Long mileage) {
    }

    /**
     * Snapshot fields come from the persisted Vehicle and are {@code null} unless the status is
     * {@link Status#REUSED} or {@link Status#CREATED}.
     */
    record VehicleIntake(
            UUID vehicleId,
            Status status,
            String licensePlate,
            String brand,
            String model,
            Integer year) {
    }

    enum Status {
        REUSED,
        CREATED,
        ARCHIVED,
        OWNED_BY_ANOTHER_CUSTOMER,
        CHASSIS_ALREADY_REGISTERED
    }
}
