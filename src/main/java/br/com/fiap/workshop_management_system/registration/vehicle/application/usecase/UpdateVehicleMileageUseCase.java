package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.UpdateVehicleMileageRequest;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleMapper;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception.VehicleNotFoundException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Mileage;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class UpdateVehicleMileageUseCase {

    private final VehicleRepository repository;

    public UpdateVehicleMileageUseCase(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public VehicleResponse execute(UUID id, UpdateVehicleMileageRequest request) {
        Objects.requireNonNull(request, "A requisição de quilometragem é obrigatória");
        Mileage mileage = new Mileage(Objects.requireNonNull(
                request.mileage(), "A quilometragem do veículo é obrigatória"));
        Vehicle vehicle = repository.findByIdForUpdate(id).orElseThrow(VehicleNotFoundException::new);

        if (vehicle.recordMileage(mileage)) {
            repository.save(vehicle);
        }
        return VehicleMapper.toResponse(vehicle);
    }
}
