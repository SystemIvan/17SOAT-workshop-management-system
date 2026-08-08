package br.com.fiap.workshop_management_system.application.parts.usecase;

import br.com.fiap.workshop_management_system.application.parts.dto.PartMapper;
import br.com.fiap.workshop_management_system.application.parts.dto.PartResponse;
import br.com.fiap.workshop_management_system.domain.parts.model.Part;
import br.com.fiap.workshop_management_system.domain.parts.repository.PartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetPartUseCase {

    private final PartRepository repository;

    public GetPartUseCase(PartRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PartResponse execute(UUID id) {
        Part part = PartFinder.getOrThrow(repository, id);
        return PartMapper.toResponse(part);
    }
}
