package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CreateCatalogServiceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.MoneyDto;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateCatalogServiceUseCaseTest {

    @Mock
    private CatalogServiceRepository repository;

    private CreateCatalogServiceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateCatalogServiceUseCase(repository);
    }

    @Test
    void createsAnActiveCatalogService() {
        CreateCatalogServiceRequest request = request("  Troca de Óleo  ", "150.00");

        CatalogServiceResponse response = useCase.execute(request);

        ArgumentCaptor<CatalogService> captor = ArgumentCaptor.forClass(CatalogService.class);
        verify(repository).save(captor.capture());
        CatalogService saved = captor.getValue();
        assertThat(response.id()).isEqualTo(saved.id());
        assertThat(response.name()).isEqualTo("Troca de Óleo");
        assertThat(response.basePrice().value()).isEqualByComparingTo("150.00");
        assertThat(response.active()).isTrue();
    }

    @Test
    void rejectsCaseInsensitiveDuplicateWithExistingServiceDetails() {
        CatalogService existing = CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName("Troca de Óleo"),
                new Money(new BigDecimal("150.00"), CurrencyCode.BRL),
                true);
        when(repository.findActiveByName(new CatalogServiceName("troca de óleo")))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(request(" TROCA DE ÓLEO ", "200.00")))
                .isInstanceOf(CatalogServiceNameAlreadyExistsException.class)
                .hasMessage("Já existe um serviço cadastrado com esse nome: "
                        + existing.id() + " - Troca de Óleo");

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validatesDomainBeforeConsultingTheRepository() {
        CreateCatalogServiceRequest request = request("   ", "150.00");

        assertThatThrownBy(() -> useCase.execute(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void createsWhenNoActiveServiceOwnsAnArchivedName() {
        CreateCatalogServiceRequest request = request("Troca de Óleo", "200.00");
        when(repository.findActiveByName(new CatalogServiceName("Troca de Óleo")))
                .thenReturn(Optional.empty());

        CatalogServiceResponse response = useCase.execute(request);

        assertThat(response.name()).isEqualTo("Troca de Óleo");
        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    private static CreateCatalogServiceRequest request(String name, String value) {
        return new CreateCatalogServiceRequest(
                name,
                new MoneyDto(new BigDecimal(value), CurrencyCode.BRL));
    }
}
