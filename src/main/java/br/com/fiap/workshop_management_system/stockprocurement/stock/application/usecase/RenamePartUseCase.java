package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.PartMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.PartResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.RenamePartRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Part;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.PartRepository;
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
