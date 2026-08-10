package br.com.fiap.workshop_management_system.technician.application.usecase;

import br.com.fiap.workshop_management_system.technician.application.dto.RenameTechnicianRequest;
import br.com.fiap.workshop_management_system.technician.application.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.technician.application.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.technician.domain.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RenameTechnicianUseCase {

    private final TechnicianRepository repository;

    public RenameTechnicianUseCase(TechnicianRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TechnicianResponse execute(UUID id, RenameTechnicianRequest request) {
        Technician technician = TechnicianFinder.getOrThrow(repository, id);
        technician.rename(request.name());
        repository.save(technician);
        return TechnicianMapper.toResponse(technician);
    }
}
