package br.com.fiap.workshop_management_system.registration.vehicle.domain.repository;

import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;

public interface VehicleRepository {

    boolean existsByLicensePlate(LicensePlate licensePlate);

    boolean existsByChassisNumber(ChassisNumber chassisNumber);

    void save(Vehicle vehicle);
}
