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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogServiceQueryUseCaseTest {

    @Mock
    private CatalogServiceRepository repository;

    private GetCatalogServiceUseCase getUseCase;
    private ListCatalogServicesUseCase listUseCase;

    @BeforeEach
    void setUp() {
        getUseCase = new GetCatalogServiceUseCase(repository);
        listUseCase = new ListCatalogServicesUseCase(repository);
    }

    @Test
    void getsAnExistingCatalogService() {
        CatalogService service = service("Alinhamento", true);
        when(repository.findById(service.id())).thenReturn(Optional.of(service));

        assertThat(getUseCase.execute(service.id()).name()).isEqualTo("Alinhamento");
    }

    @Test
    void reportsMissingCatalogService() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> getUseCase.execute(id))
                .isInstanceOf(CatalogServiceNotFoundException.class)
                .hasMessage("Serviço não encontrado no catálogo");
    }

    @Test
    void listsOnlyWhatTheActiveRepositoryQueryReturns() {
        CatalogService first = service("Alinhamento", true);
        CatalogService second = service("Balanceamento", true);
        when(repository.findAllActive()).thenReturn(List.of(first, second));

        assertThat(listUseCase.execute())
                .extracting(response -> response.name())
                .containsExactly("Alinhamento", "Balanceamento");
        verify(repository).findAllActive();
    }

    @Test
    void returnsAnEmptyList() {
        when(repository.findAllActive()).thenReturn(List.of());

        assertThat(listUseCase.execute()).isEmpty();
    }

    private static CatalogService service(String name, boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName(name),
                new Money(new BigDecimal("100.00"), CurrencyCode.BRL),
                active);
    }
}
