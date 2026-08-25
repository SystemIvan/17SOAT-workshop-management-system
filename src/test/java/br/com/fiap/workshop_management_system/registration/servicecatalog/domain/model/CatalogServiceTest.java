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

    @Test
    void renamesAnActiveServiceWithoutChangingOtherState() {
        UUID id = UUID.randomUUID();
        CatalogService service = CatalogService.reconstitute(id, NAME, PRICE, true);

        boolean changed = service.rename(new CatalogServiceName("  Alinhamento premium  "));

        assertThat(changed).isTrue();
        assertThat(service.id()).isEqualTo(id);
        assertThat(service.name().value()).isEqualTo("Alinhamento premium");
        assertThat(service.basePrice()).isEqualTo(PRICE);
        assertThat(service.active()).isTrue();
    }

    @Test
    void distinguishesAnIdenticalNameFromACaseOnlyCorrection() {
        CatalogService service = CatalogService.create(NAME, PRICE);

        assertThat(service.rename(new CatalogServiceName("  Troca de óleo  "))).isFalse();
        assertThat(service.rename(new CatalogServiceName("TROCA DE ÓLEO"))).isTrue();
        assertThat(service.name().value()).isEqualTo("TROCA DE ÓLEO");
    }

    @Test
    void updatesTheBasePriceWithoutChangingOtherState() {
        UUID id = UUID.randomUUID();
        CatalogService service = CatalogService.reconstitute(id, NAME, PRICE, true);
        Money newPrice = new Money(new BigDecimal("99.90"), CurrencyCode.BRL);

        boolean changed = service.updateBasePrice(newPrice);

        assertThat(changed).isTrue();
        assertThat(service.id()).isEqualTo(id);
        assertThat(service.name()).isEqualTo(NAME);
        assertThat(service.basePrice()).isEqualTo(newPrice);
        assertThat(service.active()).isTrue();
    }

    @Test
    void treatsTheSameMonetaryValueAsIdempotent() {
        CatalogService service = CatalogService.create(NAME, PRICE);

        boolean changed = service.updateBasePrice(new Money(new BigDecimal("150"), CurrencyCode.BRL));

        assertThat(changed).isFalse();
        assertThat(service.basePrice()).isEqualTo(PRICE);
    }

    @Test
    void rejectsNullUpdatesWithoutChangingState() {
        CatalogService service = CatalogService.create(NAME, PRICE);

        assertThatThrownBy(() -> service.rename(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O nome do serviço é obrigatório");
        assertThatThrownBy(() -> service.updateBasePrice(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O preço-base do serviço é obrigatório");
        assertThat(service.name()).isEqualTo(NAME);
        assertThat(service.basePrice()).isEqualTo(PRICE);
    }

    @Test
    void rejectsArchivedServiceBeforeIdempotencyOrNullValidation() {
        CatalogService service = CatalogService.reconstitute(UUID.randomUUID(), NAME, PRICE, false);

        assertThatThrownBy(() -> service.rename(NAME))
                .isInstanceOf(CatalogServiceArchivedException.class)
                .hasMessage("Serviço arquivado não pode ser atualizado");
        assertThatThrownBy(() -> service.updateBasePrice(PRICE))
                .isInstanceOf(CatalogServiceArchivedException.class);
        assertThatThrownBy(() -> service.rename(null))
                .isInstanceOf(CatalogServiceArchivedException.class);
        assertThatThrownBy(() -> service.updateBasePrice(null))
                .isInstanceOf(CatalogServiceArchivedException.class);
        assertThat(service.name()).isEqualTo(NAME);
        assertThat(service.basePrice()).isEqualTo(PRICE);
        assertThat(service.active()).isFalse();
    }

    @Test
    void archivesAnActiveServiceWithoutChangingItsHistoricalState() {
        UUID id = UUID.randomUUID();
        CatalogService service = CatalogService.reconstitute(id, NAME, PRICE, true);

        boolean changed = service.archive();

        assertThat(changed).isTrue();
        assertThat(service.id()).isEqualTo(id);
        assertThat(service.name()).isEqualTo(NAME);
        assertThat(service.basePrice()).isEqualTo(PRICE);
        assertThat(service.active()).isFalse();
    }

    @Test
    void treatsRepeatedArchiveAsIdempotent() {
        CatalogService service = CatalogService.reconstitute(UUID.randomUUID(), NAME, PRICE, false);

        assertThat(service.archive()).isFalse();
        assertThat(service.active()).isFalse();
    }
}
