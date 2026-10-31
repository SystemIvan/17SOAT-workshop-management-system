package br.com.fiap.workshop_management_system.registration.vehicle.application.api;

/**
 * Identifies an active Vehicle by license plate or registers a new one for the given Customer inside the caller's
 * transaction.
 *
 * <p>Invalid or missing data for a new Vehicle is reported with {@link IllegalArgumentException}. A concurrent
 * registration of the same plate or chassis is reported with {@link VehicleIntakeConflictException}.
 */
public interface VehicleIntakeApi {

    VehicleIntakeResult identifyOrRegister(VehicleIntakeCommand command);
}
