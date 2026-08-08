package br.com.fiap.workshop_management_system.application.technician.usecase;

import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.application.technician.dto.UpdateTechnicianStatusRequest;
import br.com.fiap.workshop_management_system.domain.technician.model.Technician;
import br.com.fiap.workshop_management_system.domain.technician.repository.TechnicianRepository;
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
