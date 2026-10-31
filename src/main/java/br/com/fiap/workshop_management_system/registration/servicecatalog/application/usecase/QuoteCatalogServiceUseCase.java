package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailability;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api.CatalogServiceQuote;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api.CatalogServiceQuoteApi;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class QuoteCatalogServiceUseCase implements CatalogServiceQuoteApi {

    private final CatalogServiceRepository repository;

    public QuoteCatalogServiceUseCase(CatalogServiceRepository repository) {
        this.repository = repository;
    }

    // Locks the row like CheckCatalogServiceAvailabilityUseCase, so the quoted price cannot change before the
    // consumer's transaction commits.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CatalogServiceQuote quote(UUID catalogServiceId) {
        Objects.requireNonNull(catalogServiceId, "O ID do serviço é obrigatório");
        return repository.findByIdForUpdate(catalogServiceId)
                .map(QuoteCatalogServiceUseCase::toQuote)
                .orElseGet(() -> CatalogServiceQuote.notFound(catalogServiceId));
    }

    private static CatalogServiceQuote toQuote(CatalogService service) {
        CatalogServiceAvailability availability = service.active()
                ? CatalogServiceAvailability.ACTIVE
                : CatalogServiceAvailability.ARCHIVED;
        return new CatalogServiceQuote(service.id(), availability, service.name().value(),
                service.basePrice().value(), service.basePrice().currency().name());
    }
}
