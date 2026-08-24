package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogServiceTest {

    private static final CatalogServiceName NAME = new CatalogServiceName("Troca de óleo");
    private static final Money PRICE = new Money(new BigDecimal("150.00"), CurrencyCode.BRL);

    @Test
    void createsAnActiveCatalogService() {
        CatalogService service = CatalogService.create(NAME, PRICE);

        assertThat(service.id()).isNotNull();
        assertThat(service.name()).isEqualTo(NAME);
        assertThat(service.basePrice()).isEqualTo(PRICE);
        assertThat(service.active()).isTrue();
    }

    @Test
    void reconstitutesThePersistedState() {
        UUID id = UUID.randomUUID();

        CatalogService service = CatalogService.reconstitute(id, NAME, PRICE, false);

        assertThat(service.id()).isEqualTo(id);
        assertThat(service.active()).isFalse();
    }

    @Test
    void rejectsMissingRequiredState() {
        assertThatThrownBy(() -> CatalogService.reconstitute(null, NAME, PRICE, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogService.reconstitute(UUID.randomUUID(), null, PRICE, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CatalogService.reconstitute(UUID.randomUUID(), NAME, null, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
