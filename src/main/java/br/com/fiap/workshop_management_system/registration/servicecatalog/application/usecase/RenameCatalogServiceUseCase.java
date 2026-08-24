package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceMapper;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.RenameCatalogServiceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNotFoundException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RenameCatalogServiceUseCase {

    private final CatalogServiceRepository repository;

    public RenameCatalogServiceUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CatalogServiceResponse execute(UUID id, RenameCatalogServiceRequest request) {
        CatalogServiceName newName = new CatalogServiceName(request.name());
        CatalogService catalogService = repository.findByIdForUpdate(id)
                .orElseThrow(CatalogServiceNotFoundException::new);

        if (!catalogService.rename(newName)) {
            return CatalogServiceMapper.toResponse(catalogService);
        }

        repository.findByName(newName)
                .filter(existing -> !existing.id().equals(id))
                .ifPresent(existing -> {
                    throw new CatalogServiceNameAlreadyExistsException(
                            existing.id(), existing.name().value());
                });

        repository.save(catalogService);
        return CatalogServiceMapper.toResponse(catalogService);
    }
}
