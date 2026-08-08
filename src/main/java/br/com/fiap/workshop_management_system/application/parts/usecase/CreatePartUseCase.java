package br.com.fiap.workshop_management_system.application.parts.usecase;

import br.com.fiap.workshop_management_system.application.parts.dto.CreatePartRequest;
import br.com.fiap.workshop_management_system.application.parts.dto.PartMapper;
import br.com.fiap.workshop_management_system.application.parts.dto.PartResponse;
import br.com.fiap.workshop_management_system.domain.parts.model.Part;
import br.com.fiap.workshop_management_system.domain.parts.repository.PartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreatePartUseCase {

    private final PartRepository repository;

    public CreatePartUseCase(PartRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PartResponse execute(CreatePartRequest request) {
        Part part = Part.create(request.name(), request.sku(), request.initialQuantity(), PartMapper.toPrice(request.price()));
        repository.save(part);
        return PartMapper.toResponse(part);
    }
}
