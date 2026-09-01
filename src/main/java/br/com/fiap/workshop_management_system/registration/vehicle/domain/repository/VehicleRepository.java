package br.com.fiap.workshop_management_system.registration.vehicle.domain.repository;

import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository {

    boolean existsByLicensePlate(LicensePlate licensePlate);

    boolean existsByChassisNumber(ChassisNumber chassisNumber);

    Optional<Vehicle> findByIdForUpdate(UUID id);

    Optional<Vehicle> findById(UUID id);

    List<Vehicle> findAllActive();

    boolean existsByChassisNumberAndIdNot(ChassisNumber chassisNumber, UUID id);

    void save(Vehicle vehicle);
}
