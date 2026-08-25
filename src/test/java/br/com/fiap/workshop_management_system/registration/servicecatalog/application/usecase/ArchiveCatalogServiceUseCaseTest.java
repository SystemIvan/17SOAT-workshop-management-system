package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNotFoundException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveCatalogServiceUseCaseTest {

    @Mock
    private CatalogServiceRepository repository;

    private ArchiveCatalogServiceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ArchiveCatalogServiceUseCase(repository);
    }

    @Test
    void archivesAnActiveService() {
        CatalogService catalogService = service(true);
        when(repository.findByIdForUpdate(catalogService.id())).thenReturn(Optional.of(catalogService));

        useCase.execute(catalogService.id());

        assertThat(catalogService.active()).isFalse();
        verify(repository).save(catalogService);
    }

    @Test
    void returnsIdempotentlyWithoutSavingAnArchivedService() {
        CatalogService catalogService = service(false);
        when(repository.findByIdForUpdate(catalogService.id())).thenReturn(Optional.of(catalogService));

        useCase.execute(catalogService.id());

        assertThat(catalogService.active()).isFalse();
        verify(repository, never()).save(catalogService);
    }

    @Test
    void reportsMissingServiceWithoutSaving() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id))
                .isInstanceOf(CatalogServiceNotFoundException.class);

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static CatalogService service(boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName("Alinhamento"),
                new Money(new BigDecimal("100.00"), CurrencyCode.BRL),
                active);
    }
}
