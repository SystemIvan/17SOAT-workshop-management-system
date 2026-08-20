package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import org.springframework.stereotype.Component;

@Component
public class VehiclePersistenceMapper {

    public VehicleJpaEntity toEntity(Vehicle vehicle) {
        return new VehicleJpaEntity(
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

    public Vehicle toDomain(VehicleJpaEntity entity) {
        ChassisNumber chassisNumber = entity.getChassisNumber() == null
                ? null
                : new ChassisNumber(entity.getChassisNumber());
        return Vehicle.reconstitute(
                entity.getId(),
                entity.getCustomerId(),
                new LicensePlate(entity.getLicensePlate()),
                chassisNumber,
                entity.getBrand(),
                entity.getModel(),
                VehicleYear.reconstitute(entity.getModelYear()),
                entity.getColor(),
                entity.isActive());
    }
}
