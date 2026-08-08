package br.com.fiap.workshop_management_system.application.parts.usecase;

import br.com.fiap.workshop_management_system.application.parts.dto.PartMapper;
import br.com.fiap.workshop_management_system.application.parts.dto.PartResponse;
import br.com.fiap.workshop_management_system.domain.parts.repository.PartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListPartsUseCase {

    private final PartRepository repository;

    public ListPartsUseCase(PartRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<PartResponse> execute() {
        return repository.findAll().stream().map(PartMapper::toResponse).toList();
    }
}
