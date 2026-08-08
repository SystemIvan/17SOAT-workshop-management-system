package br.com.fiap.workshop_management_system.application.technician.usecase;

import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.domain.technician.model.Technician;
import br.com.fiap.workshop_management_system.domain.technician.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetTechnicianUseCase {

    private final TechnicianRepository repository;

    public GetTechnicianUseCase(TechnicianRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public TechnicianResponse execute(UUID id) {
        Technician technician = TechnicianFinder.getOrThrow(repository, id);
        return TechnicianMapper.toResponse(technician);
    }
}
