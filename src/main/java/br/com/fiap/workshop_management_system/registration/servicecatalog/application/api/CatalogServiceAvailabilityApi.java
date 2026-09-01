package br.com.fiap.workshop_management_system.registration.servicecatalog.application.api;

import java.util.UUID;

public interface CatalogServiceAvailabilityApi {

    CatalogServiceAvailability checkForNewWork(UUID catalogServiceId);
}
