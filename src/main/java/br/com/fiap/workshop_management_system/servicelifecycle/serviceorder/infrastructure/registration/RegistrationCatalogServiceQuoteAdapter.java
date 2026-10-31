package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api.CatalogServiceQuoteApi;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibility;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CatalogServiceQuotePort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RegistrationCatalogServiceQuoteAdapter implements CatalogServiceQuotePort {

    private final CatalogServiceQuoteApi catalogServiceQuoteApi;

    public RegistrationCatalogServiceQuoteAdapter(CatalogServiceQuoteApi catalogServiceQuoteApi) {
        this.catalogServiceQuoteApi = catalogServiceQuoteApi;
    }

    @Override
    public CatalogServiceQuote quote(UUID catalogServiceId) {
        var quote = catalogServiceQuoteApi.quote(catalogServiceId);
        CatalogServiceEligibility eligibility = switch (quote.availability()) {
            case ACTIVE -> CatalogServiceEligibility.ACTIVE;
            case ARCHIVED -> CatalogServiceEligibility.ARCHIVED;
            case NOT_FOUND -> CatalogServiceEligibility.NOT_FOUND;
        };
        Money price = quote.price() == null ? null : new Money(quote.price(), quote.currency());
        return new CatalogServiceQuote(quote.id(), eligibility, quote.name(), price);
    }
}
