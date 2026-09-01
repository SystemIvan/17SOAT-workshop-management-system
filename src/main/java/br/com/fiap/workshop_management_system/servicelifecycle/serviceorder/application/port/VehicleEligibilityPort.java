package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import java.util.UUID;

public interface VehicleEligibilityPort {

    VehicleEligibility checkForNewWork(UUID vehicleId);
}
