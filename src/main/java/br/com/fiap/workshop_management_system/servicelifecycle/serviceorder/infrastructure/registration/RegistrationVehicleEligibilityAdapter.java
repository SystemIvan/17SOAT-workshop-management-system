package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailability;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailabilityApi;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.VehicleEligibility;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.VehicleEligibilityPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RegistrationVehicleEligibilityAdapter implements VehicleEligibilityPort {

    private final VehicleAvailabilityApi availabilityApi;

    public RegistrationVehicleEligibilityAdapter(VehicleAvailabilityApi availabilityApi) {
        this.availabilityApi = availabilityApi;
    }

    @Override
    public VehicleEligibility checkForNewWork(UUID vehicleId) {
        VehicleAvailability availability = availabilityApi.checkForNewWork(vehicleId);
        return switch (availability) {
            case ACTIVE -> VehicleEligibility.ACTIVE;
            case ARCHIVED -> VehicleEligibility.ARCHIVED;
            case NOT_FOUND -> VehicleEligibility.NOT_FOUND;
        };
    }
}
