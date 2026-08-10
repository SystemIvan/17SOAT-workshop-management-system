package br.com.fiap.workshop_management_system.technician.application.usecase;

import br.com.fiap.workshop_management_system.technician.application.dto.TechnicianMapper;
import br.com.fiap.workshop_management_system.technician.application.dto.TechnicianResponse;
import br.com.fiap.workshop_management_system.technician.domain.repository.TechnicianRepository;
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
