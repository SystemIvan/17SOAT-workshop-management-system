package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api
        .CatalogServiceAvailability;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.api.CatalogServiceQuote;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteCatalogServiceUseCaseTest {

    @Mock
    private CatalogServiceRepository repository;

    private QuoteCatalogServiceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new QuoteCatalogServiceUseCase(repository);
    }

    @Test
    void quotesActiveServiceWithCatalogNameAndBasePrice() {
        CatalogService service = service();
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        CatalogServiceQuote quote = useCase.quote(service.id());

        assertEquals(service.id(), quote.id());
        assertEquals(CatalogServiceAvailability.ACTIVE, quote.availability());
        assertEquals("Troca de óleo", quote.name());
        assertEquals(new BigDecimal("150.00"), quote.price());
        assertEquals("BRL", quote.currency());
    }

    @Test
    void reportsArchivedService() {
        CatalogService service = service();
        service.archive();
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        assertEquals(CatalogServiceAvailability.ARCHIVED, useCase.quote(service.id()).availability());
    }

    @Test
    void reportsMissingServiceWithoutCommercialData() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        CatalogServiceQuote quote = useCase.quote(id);

        assertEquals(CatalogServiceAvailability.NOT_FOUND, quote.availability());
        assertEquals(id, quote.id());
        assertNull(quote.name());
        assertNull(quote.price());
    }

    private static CatalogService service() {
        return CatalogService.create(new CatalogServiceName("Troca de óleo"),
                new Money(new BigDecimal("150.00"), CurrencyCode.BRL));
    }
}
