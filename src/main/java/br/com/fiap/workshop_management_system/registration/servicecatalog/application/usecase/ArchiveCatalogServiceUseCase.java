package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNotFoundException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ArchiveCatalogServiceUseCase {

    private final CatalogServiceRepository repository;

    public ArchiveCatalogServiceUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(UUID id) {
        CatalogService catalogService = repository.findByIdForUpdate(id)
                .orElseThrow(CatalogServiceNotFoundException::new);

        if (catalogService.archive()) {
            repository.save(catalogService);
        }
    }
}
