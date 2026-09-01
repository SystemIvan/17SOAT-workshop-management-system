package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleMapper;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.application.exception.VehicleNotFoundException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetVehicleUseCase {

    private final VehicleRepository repository;

    public GetVehicleUseCase(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public VehicleResponse execute(UUID id) {
        Vehicle vehicle = repository.findById(id).orElseThrow(VehicleNotFoundException::new);
        return VehicleMapper.toResponse(vehicle);
    }
}
