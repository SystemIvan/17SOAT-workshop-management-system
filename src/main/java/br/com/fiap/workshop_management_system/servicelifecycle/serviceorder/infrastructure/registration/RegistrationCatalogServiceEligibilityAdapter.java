package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailabilityApi;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibility;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibilityPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RegistrationCatalogServiceEligibilityAdapter implements CatalogServiceEligibilityPort {

    private final CatalogServiceAvailabilityApi availabilityApi;

    public RegistrationCatalogServiceEligibilityAdapter(CatalogServiceAvailabilityApi availabilityApi) {
        this.availabilityApi = availabilityApi;
    }

    @Override
    public CatalogServiceEligibility checkForNewWork(UUID catalogServiceId) {
        return switch (availabilityApi.checkForNewWork(catalogServiceId)) {
            case ACTIVE -> CatalogServiceEligibility.ACTIVE;
            case ARCHIVED -> CatalogServiceEligibility.ARCHIVED;
            case NOT_FOUND -> CatalogServiceEligibility.NOT_FOUND;
        };
    }
}
