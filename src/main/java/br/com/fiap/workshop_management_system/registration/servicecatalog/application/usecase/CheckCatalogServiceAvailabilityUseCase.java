package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailability;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailabilityApi;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CheckCatalogServiceAvailabilityUseCase implements CatalogServiceAvailabilityApi {

    private final CatalogServiceRepository repository;

    public CheckCatalogServiceAvailabilityUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CatalogServiceAvailability checkForNewWork(UUID catalogServiceId) {
        return repository.findByIdForUpdate(catalogServiceId)
                .map(service -> service.active()
                        ? CatalogServiceAvailability.ACTIVE
                        : CatalogServiceAvailability.ARCHIVED)
                .orElse(CatalogServiceAvailability.NOT_FOUND);
    }
}
