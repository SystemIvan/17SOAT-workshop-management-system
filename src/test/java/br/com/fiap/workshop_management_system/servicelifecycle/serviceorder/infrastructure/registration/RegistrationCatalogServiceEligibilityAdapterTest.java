package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailability;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailabilityApi;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibility;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegistrationCatalogServiceEligibilityAdapterTest {

    private final CatalogServiceAvailabilityApi availabilityApi = mock(CatalogServiceAvailabilityApi.class);
    private final RegistrationCatalogServiceEligibilityAdapter adapter =
            new RegistrationCatalogServiceEligibilityAdapter(availabilityApi);

    @Test
    void mapsAllProducerStatesToConsumerStates() {
        UUID active = UUID.randomUUID();
        UUID archived = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(availabilityApi.checkForNewWork(active)).thenReturn(CatalogServiceAvailability.ACTIVE);
        when(availabilityApi.checkForNewWork(archived)).thenReturn(CatalogServiceAvailability.ARCHIVED);
        when(availabilityApi.checkForNewWork(missing)).thenReturn(CatalogServiceAvailability.NOT_FOUND);

        assertThat(adapter.checkForNewWork(active)).isEqualTo(CatalogServiceEligibility.ACTIVE);
        assertThat(adapter.checkForNewWork(archived)).isEqualTo(CatalogServiceEligibility.ARCHIVED);
        assertThat(adapter.checkForNewWork(missing)).isEqualTo(CatalogServiceEligibility.NOT_FOUND);
    }
}
