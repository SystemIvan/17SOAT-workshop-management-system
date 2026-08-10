package br.com.fiap.workshop_management_system.parts.application.usecase;

import br.com.fiap.workshop_management_system.parts.application.dto.PartMapper;
import br.com.fiap.workshop_management_system.parts.application.dto.PartResponse;
import br.com.fiap.workshop_management_system.parts.application.dto.UpdatePartPriceRequest;
import br.com.fiap.workshop_management_system.parts.domain.model.Part;
import br.com.fiap.workshop_management_system.parts.domain.repository.PartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdatePartPriceUseCase {

    private final PartRepository repository;

    public UpdatePartPriceUseCase(PartRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PartResponse execute(UUID id, UpdatePartPriceRequest request) {
        Part part = PartFinder.getOrThrow(repository, id);
        part.changePrice(PartMapper.toPrice(request.price()));
        repository.save(part);
        return PartMapper.toResponse(part);
    }
}
