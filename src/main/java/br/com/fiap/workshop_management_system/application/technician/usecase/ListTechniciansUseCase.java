package br.com.fiap.workshop_management_system.application.technician.usecase;

import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.application.technician.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.domain.technician.repository.TechnicianRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListTechniciansUseCase {

    private final TechnicianRepository repository;

    public ListTechniciansUseCase(TechnicianRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TechnicianResponse> execute() {
        return repository.findAll().stream().map(TechnicianMapper::toResponse).toList();
    }
}
