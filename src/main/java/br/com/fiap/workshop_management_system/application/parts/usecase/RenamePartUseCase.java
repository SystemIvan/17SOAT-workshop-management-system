package br.com.fiap.workshop_management_system.application.parts.usecase;

import br.com.fiap.workshop_management_system.application.parts.dto.PartMapper;
import br.com.fiap.workshop_management_system.application.parts.dto.PartResponse;
import br.com.fiap.workshop_management_system.application.parts.dto.RenamePartRequest;
import br.com.fiap.workshop_management_system.domain.parts.model.Part;
import br.com.fiap.workshop_management_system.domain.parts.repository.PartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RenamePartUseCase {

    private final PartRepository repository;

    public RenamePartUseCase(PartRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PartResponse execute(UUID id, RenamePartRequest request) {
        Part part = PartFinder.getOrThrow(repository, id);
        part.rename(request.name());
        repository.save(part);
        return PartMapper.toResponse(part);
    }
}
