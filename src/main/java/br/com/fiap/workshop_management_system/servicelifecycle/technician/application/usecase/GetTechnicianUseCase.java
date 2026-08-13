package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
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
