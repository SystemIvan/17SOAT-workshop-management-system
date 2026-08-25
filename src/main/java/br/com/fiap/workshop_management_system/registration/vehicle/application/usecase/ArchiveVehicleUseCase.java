package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.exception.VehicleNotFoundException;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ArchiveVehicleUseCase {

    private final VehicleRepository repository;

    public ArchiveVehicleUseCase(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(UUID id) {
        Vehicle vehicle = repository.findByIdForUpdate(id).orElseThrow(VehicleNotFoundException::new);
        if (vehicle.archive()) {
            repository.save(vehicle);
        }
    }
}
