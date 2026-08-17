package br.com.fiap.workshop_management_system.registration.vehicle.application.dto;

import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;

public final class VehicleMapper {

    private VehicleMapper() {
    }

    public static VehicleResponse toResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.id(),
                vehicle.customerId(),
                vehicle.licensePlate().value(),
                vehicle.chassisNumber().map(chassis -> chassis.value()).orElse(null),
                vehicle.brand(),
                vehicle.model(),
                vehicle.year().value(),
                vehicle.color(),
                vehicle.active());
    }
}
