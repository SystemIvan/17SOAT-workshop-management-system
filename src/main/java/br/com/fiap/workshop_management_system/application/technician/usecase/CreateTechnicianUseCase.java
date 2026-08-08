package br.com.fiap.workshop_management_system.application.technician.usecase;

import br.com.fiap.workshop_management_system.application.technician.dto.CreateTechnicianRequest;
import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.domain.technician.model.Technician;
import br.com.fiap.workshop_management_system.domain.technician.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateTechnicianUseCase {

    private final TechnicianRepository repository;

    public CreateTechnicianUseCase(TechnicianRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TechnicianResponse execute(CreateTechnicianRequest request) {
        Technician technician = Technician.create(request.name(), request.specialties());
        repository.save(technician);
        return TechnicianMapper.toResponse(technician);
    }
}
