package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailability;
import br.com.fiap.workshop_management_system.registration.vehicle.application.api.VehicleAvailabilityApi;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.model.Vehicle;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class CheckVehicleAvailabilityUseCase implements VehicleAvailabilityApi {

    private final VehicleRepository repository;

    public CheckVehicleAvailabilityUseCase(VehicleRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public VehicleAvailability checkForNewWork(UUID vehicleId) {
        Objects.requireNonNull(vehicleId, "O ID do veículo é obrigatório");
        return repository.findByIdForUpdate(vehicleId)
                .map(CheckVehicleAvailabilityUseCase::availabilityOf)
                .orElse(VehicleAvailability.NOT_FOUND);
    }

    private static VehicleAvailability availabilityOf(Vehicle vehicle) {
        return vehicle.active() ? VehicleAvailability.ACTIVE : VehicleAvailability.ARCHIVED;
    }
}
