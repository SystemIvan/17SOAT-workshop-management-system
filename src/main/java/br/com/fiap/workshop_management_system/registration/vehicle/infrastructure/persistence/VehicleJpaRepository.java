package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleJpaRepository extends JpaRepository<VehicleJpaEntity, UUID> {

    boolean existsByLicensePlate(String licensePlate);

    boolean existsByChassisNumber(String chassisNumber);

    boolean existsByChassisNumberAndIdNot(String chassisNumber, UUID id);

    List<VehicleJpaEntity> findAllByActiveTrue();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select vehicle from VehicleJpaEntity vehicle where vehicle.id = :id")
    Optional<VehicleJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
