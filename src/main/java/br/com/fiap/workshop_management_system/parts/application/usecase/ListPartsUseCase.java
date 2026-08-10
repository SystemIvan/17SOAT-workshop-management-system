package br.com.fiap.workshop_management_system.parts.application.usecase;

import br.com.fiap.workshop_management_system.parts.application.dto.PartMapper;
import br.com.fiap.workshop_management_system.parts.application.dto.PartResponse;
import br.com.fiap.workshop_management_system.parts.domain.repository.PartRepository;
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
