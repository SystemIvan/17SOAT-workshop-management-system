package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleMapper;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception
        .VehicleChassisAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception.VehicleNotFoundException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.ChassisNumber;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.VehicleYear;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Year;
import java.util.UUID;

@Service
public class UpdateVehicleUseCase {

    private final VehicleRepository repository;
    private final Clock clock;

    public UpdateVehicleUseCase(VehicleRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public VehicleResponse execute(UUID id, UpdateVehicleRequest request) {
        VehicleYear year = VehicleYear.create(request.year(), Year.now(clock).getValue());
        ChassisNumber chassisUpdate = hasChassisUpdate(request.chassis())
                ? new ChassisNumber(request.chassis())
                : null;

        Vehicle vehicle = repository.findByIdForUpdate(id).orElseThrow(VehicleNotFoundException::new);
        ChassisNumber currentChassis = vehicle.chassisNumber().orElse(null);
        boolean chassisChanged = chassisUpdate != null && !chassisUpdate.equals(currentChassis);

        vehicle.updateDetails(request.brand(), request.model(), year, request.color(), chassisUpdate);
        if (chassisChanged && repository.existsByChassisNumberAndIdNot(chassisUpdate, id)) {
            throw new VehicleChassisAlreadyExistsException();
        }

        repository.save(vehicle);
        return VehicleMapper.toResponse(vehicle);
    }

    private static boolean hasChassisUpdate(String chassis) {
        return chassis != null && !chassis.isBlank();
    }
}
