package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceMapper;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNotFoundException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetCatalogServiceUseCase {

    private final CatalogServiceRepository repository;

    public GetCatalogServiceUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CatalogServiceResponse execute(UUID id) {
        CatalogService catalogService = repository.findById(id)
                .orElseThrow(CatalogServiceNotFoundException::new);
        return CatalogServiceMapper.toResponse(catalogService);
    }
}
