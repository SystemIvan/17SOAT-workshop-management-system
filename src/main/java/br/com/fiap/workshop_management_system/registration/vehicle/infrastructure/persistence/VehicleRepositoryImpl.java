package br.com.fiap.workshop_management_system.registration.vehicle.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleLicensePlateAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.LicensePlate;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class VehicleRepositoryImpl implements VehicleRepository {

    private static final String LICENSE_PLATE_CONSTRAINT = "uk_vehicles_license_plate";
    private static final String CHASSIS_CONSTRAINT = "uk_vehicles_chassis_number";

    private final VehicleJpaRepository jpaRepository;
    private final VehiclePersistenceMapper mapper;

    public VehicleRepositoryImpl(VehicleJpaRepository jpaRepository, VehiclePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public boolean existsByLicensePlate(LicensePlate licensePlate) {
        return jpaRepository.existsByLicensePlate(licensePlate.value());
    }

    @Override
    public boolean existsByChassisNumber(ChassisNumber chassisNumber) {
        return jpaRepository.existsByChassisNumber(chassisNumber.value());
    }

    @Override
    public Optional<Vehicle> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public boolean existsByChassisNumberAndIdNot(ChassisNumber chassisNumber, UUID id) {
        return jpaRepository.existsByChassisNumberAndIdNot(chassisNumber.value(), id);
    }

    @Override
    public void save(Vehicle vehicle) {
        try {
            jpaRepository.saveAndFlush(mapper.toEntity(vehicle));
        } catch (DataIntegrityViolationException exception) {
            String message = exception.getMostSpecificCause().getMessage();
            String normalizedMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
            if (normalizedMessage.contains(LICENSE_PLATE_CONSTRAINT)) {
                throw new VehicleLicensePlateAlreadyExistsException();
            }
            if (normalizedMessage.contains(CHASSIS_CONSTRAINT)) {
                throw new VehicleChassisAlreadyExistsException();
            }
            throw exception;
        }
    }
}
