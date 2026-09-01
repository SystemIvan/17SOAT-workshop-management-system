package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.MoneyDto;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto
        .UpdateCatalogServiceBasePriceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNotFoundException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceArchivedException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateCatalogServiceBasePriceUseCaseTest {

    @Mock
    private CatalogServiceRepository repository;

    private UpdateCatalogServiceBasePriceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateCatalogServiceBasePriceUseCase(repository);
    }

    @Test
    void updatesThePriceAndReturnsTheCompleteResponse() {
        CatalogService service = service(true);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        CatalogServiceResponse response = useCase.execute(service.id(), request("89.90"));

        assertThat(response.id()).isEqualTo(service.id());
        assertThat(response.name()).isEqualTo("Alinhamento");
        assertThat(response.basePrice().value()).isEqualByComparingTo("89.90");
        assertThat(response.active()).isTrue();
        verify(repository).save(service);
    }

    @Test
    void returnsIdempotentlyWithoutSaveForTheSameMonetaryValue() {
        CatalogService service = service(true);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        CatalogServiceResponse response = useCase.execute(service.id(), request("100"));

        assertThat(response.basePrice().value()).isEqualByComparingTo("100.00");
        verify(repository, never()).save(any());
    }

    @Test
    void validatesMoneyBeforeConsultingTheRepository() {
        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), request("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullMoneyBeforeConsultingTheRepository() {
        UpdateCatalogServiceBasePriceRequest request = new UpdateCatalogServiceBasePriceRequest(null);

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void reportsMissingServiceWithoutSave() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, request("89.90")))
                .isInstanceOf(CatalogServiceNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsArchivedServiceBeforeIdempotency() {
        CatalogService service = service(false);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> useCase.execute(service.id(), request("100.00")))
                .isInstanceOf(CatalogServiceArchivedException.class);

        verify(repository, never()).save(any());
    }

    private static UpdateCatalogServiceBasePriceRequest request(String value) {
        return new UpdateCatalogServiceBasePriceRequest(
                new MoneyDto(new BigDecimal(value), CurrencyCode.BRL));
    }

    private static CatalogService service(boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName("Alinhamento"),
                new Money(new BigDecimal("100.00"), CurrencyCode.BRL),
                active);
    }
}
