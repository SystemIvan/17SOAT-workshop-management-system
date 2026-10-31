package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;

import java.util.UUID;

public interface CatalogServiceQuotePort {

    CatalogServiceQuote quote(UUID catalogServiceId);

    /**
     * {@code name} and {@code price} are {@code null} when the eligibility is
     * {@link CatalogServiceEligibility#NOT_FOUND}.
     */
    record CatalogServiceQuote(UUID id, CatalogServiceEligibility eligibility, String name, Money price) {
    }
}
