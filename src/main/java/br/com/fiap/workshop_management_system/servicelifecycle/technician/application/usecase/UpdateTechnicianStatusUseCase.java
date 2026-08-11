package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.UpdateTechnicianStatusRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateTechnicianStatusUseCase {

    private final TechnicianRepository repository;

    public UpdateTechnicianStatusUseCase(TechnicianRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TechnicianResponse execute(UUID id, UpdateTechnicianStatusRequest request) {
        Technician technician = TechnicianFinder.getOrThrow(repository, id);
        switch (request.status()) {
            case AVAILABLE -> technician.markAvailable();
            case BUSY -> technician.markBusy();
            case INACTIVE -> technician.deactivate();
        }
        repository.save(technician);
        return TechnicianMapper.toResponse(technician);
    }
}
