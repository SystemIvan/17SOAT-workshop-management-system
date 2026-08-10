package br.com.fiap.workshop_management_system.parts.application.usecase;

import br.com.fiap.workshop_management_system.parts.application.dto.PartMapper;
import br.com.fiap.workshop_management_system.parts.application.dto.PartResponse;
import br.com.fiap.workshop_management_system.parts.domain.model.Part;
import br.com.fiap.workshop_management_system.parts.domain.repository.PartRepository;
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
