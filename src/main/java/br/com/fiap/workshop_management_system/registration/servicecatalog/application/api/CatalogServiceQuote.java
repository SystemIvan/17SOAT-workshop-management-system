package br.com.fiap.workshop_management_system.registration.servicecatalog.application.api;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code name}, {@code price} and {@code currency} are {@code null} when the availability is
 * {@link CatalogServiceAvailability#NOT_FOUND}.
 */
public record CatalogServiceQuote(
        UUID id,
        CatalogServiceAvailability availability,
        String name,
        BigDecimal price,
        String currency) {

    public static CatalogServiceQuote notFound(UUID id) {
        return new CatalogServiceQuote(id, CatalogServiceAvailability.NOT_FOUND, null, null, null);
    }
}
