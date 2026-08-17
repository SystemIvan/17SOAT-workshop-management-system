package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VehicleJpaRepository extends JpaRepository<VehicleJpaEntity, UUID> {

    boolean existsByLicensePlate(String licensePlate);

    boolean existsByChassisNumber(String chassisNumber);
}
