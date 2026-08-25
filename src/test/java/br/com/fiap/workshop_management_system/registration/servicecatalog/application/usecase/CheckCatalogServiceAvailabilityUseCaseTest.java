package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailability;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckCatalogServiceAvailabilityUseCaseTest {

    @Mock
    private CatalogServiceRepository repository;

    private CheckCatalogServiceAvailabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CheckCatalogServiceAvailabilityUseCase(repository);
    }

    @Test
    void reportsActiveService() {
        CatalogService service = service(true);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        assertThat(useCase.checkForNewWork(service.id())).isEqualTo(CatalogServiceAvailability.ACTIVE);
    }

    @Test
    void reportsArchivedService() {
        CatalogService service = service(false);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        assertThat(useCase.checkForNewWork(service.id())).isEqualTo(CatalogServiceAvailability.ARCHIVED);
    }

    @Test
    void reportsMissingService() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThat(useCase.checkForNewWork(id)).isEqualTo(CatalogServiceAvailability.NOT_FOUND);
    }

    private static CatalogService service(boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName("Revisão"),
                new Money(new BigDecimal("100.00"), CurrencyCode.BRL),
                active);
    }
}
