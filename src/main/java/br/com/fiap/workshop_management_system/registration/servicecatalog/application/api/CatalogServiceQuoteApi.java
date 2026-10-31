package br.com.fiap.workshop_management_system.registration.servicecatalog.application.api;

import java.util.UUID;

/**
 * Returns the current name and base price of a Catalog Service inside the caller's transaction, so consumers copy
 * commercial data from the catalog instead of trusting values sent by their own clients.
 */
public interface CatalogServiceQuoteApi {

    CatalogServiceQuote quote(UUID catalogServiceId);
}
