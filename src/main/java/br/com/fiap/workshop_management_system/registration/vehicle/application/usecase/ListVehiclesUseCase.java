package br.com.fiap.workshop_management_system.registration.vehicle.application.usecase;

import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleMapper;
import br.com.fiap.workshop_management_system.registration.vehicle.application.dto.VehicleResponse;
import br.com.fiap.workshop_management_system.registration.vehicle.domain.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListVehiclesUseCase {

    private final VehicleRepository repository;

    public ListVehiclesUseCase(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> execute() {
        return repository.findAllActive().stream()
                .map(VehicleMapper::toResponse)
                .toList();
    }
}
