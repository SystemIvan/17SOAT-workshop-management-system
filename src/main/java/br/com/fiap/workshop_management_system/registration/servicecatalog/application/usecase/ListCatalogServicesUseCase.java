package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceMapper;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListCatalogServicesUseCase {

    private final CatalogServiceRepository repository;

    public ListCatalogServicesUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CatalogServiceResponse> execute() {
        return repository.findAllActive().stream()
                .map(CatalogServiceMapper::toResponse)
                .toList();
    }
}
