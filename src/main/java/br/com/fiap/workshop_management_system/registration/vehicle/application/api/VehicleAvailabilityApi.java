package br.com.fiap.workshop_management_system.registration.vehicle.application.api;

import java.util.UUID;

public interface VehicleAvailabilityApi {

    VehicleAvailability checkForNewWork(UUID vehicleId);
}
