package br.com.fiap.workshop_management_system.registration.servicecatalog.application.usecase;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.CatalogServiceResponse;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.dto.RenameCatalogServiceRequest;
import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
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
class RenameCatalogServiceUseCaseTest {

    @Mock
    private CatalogServiceRepository repository;

    private RenameCatalogServiceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RenameCatalogServiceUseCase(repository);
    }

    @Test
    void renamesAnActiveServiceAndReturnsTheCompleteResponse() {
        CatalogService service = service("Alinhamento", true);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));
        when(repository.findActiveByName(new CatalogServiceName("Alinhamento Premium")))
                .thenReturn(Optional.empty());

        CatalogServiceResponse response = useCase.execute(
                service.id(), new RenameCatalogServiceRequest("  Alinhamento Premium  "));

        assertThat(response.id()).isEqualTo(service.id());
        assertThat(response.name()).isEqualTo("Alinhamento Premium");
        assertThat(response.basePrice().value()).isEqualByComparingTo("100.00");
        assertThat(response.active()).isTrue();
        verify(repository).save(service);
    }

    @Test
    void returnsIdempotentlyWithoutConflictLookupOrSaveForTheSameDisplayName() {
        CatalogService service = service("Alinhamento", true);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        CatalogServiceResponse response = useCase.execute(
                service.id(), new RenameCatalogServiceRequest("  Alinhamento  "));

        assertThat(response.name()).isEqualTo("Alinhamento");
        verify(repository, never()).findActiveByName(any());
        verify(repository, never()).save(any());
    }

    @Test
    void allowsACaseOnlyCorrectionWhenTheCanonicalNameBelongsToTheTarget() {
        CatalogService service = service("alinhamento", true);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));
        when(repository.findActiveByName(new CatalogServiceName("ALINHAMENTO")))
                .thenReturn(Optional.of(service));

        CatalogServiceResponse response = useCase.execute(
                service.id(), new RenameCatalogServiceRequest("ALINHAMENTO"));

        assertThat(response.name()).isEqualTo("ALINHAMENTO");
        verify(repository).save(service);
    }

    @Test
    void rejectsANameOwnedByAnotherActiveService() {
        CatalogService target = service("Alinhamento", true);
        CatalogService existing = service("Balanceamento", true);
        when(repository.findByIdForUpdate(target.id())).thenReturn(Optional.of(target));
        when(repository.findActiveByName(new CatalogServiceName("BALANCEAMENTO")))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(
                target.id(), new RenameCatalogServiceRequest(" BALANCEAMENTO ")))
                .isInstanceOf(CatalogServiceNameAlreadyExistsException.class)
                .hasMessage("Já existe um serviço cadastrado com esse nome: "
                        + existing.id() + " - Balanceamento");

        verify(repository, never()).save(any());
    }

    @Test
    void allowsANameUsedOnlyByArchivedServices() {
        CatalogService target = service("Alinhamento", true);
        when(repository.findByIdForUpdate(target.id())).thenReturn(Optional.of(target));
        when(repository.findActiveByName(new CatalogServiceName("BALANCEAMENTO")))
                .thenReturn(Optional.empty());

        CatalogServiceResponse response = useCase.execute(
                target.id(), new RenameCatalogServiceRequest(" BALANCEAMENTO "));

        assertThat(response.name()).isEqualTo("BALANCEAMENTO");
        verify(repository).save(target);
    }

    @Test
    void rejectsInvalidNameBeforeConsultingTheRepository() {
        assertThatThrownBy(() -> useCase.execute(
                UUID.randomUUID(), new RenameCatalogServiceRequest("   ")))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void reportsMissingServiceWithoutConflictLookupOrSave() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(id, new RenameCatalogServiceRequest("Alinhamento")))
                .isInstanceOf(CatalogServiceNotFoundException.class);

        verify(repository, never()).findActiveByName(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsArchivedServiceBeforeIdempotencyOrConflictLookup() {
        CatalogService service = service("Alinhamento", false);
        when(repository.findByIdForUpdate(service.id())).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> useCase.execute(
                service.id(), new RenameCatalogServiceRequest("Alinhamento")))
                .isInstanceOf(CatalogServiceArchivedException.class);

        verify(repository, never()).findActiveByName(any());
        verify(repository, never()).save(any());
    }

    private static CatalogService service(String name, boolean active) {
        return CatalogService.reconstitute(
                UUID.randomUUID(),
                new CatalogServiceName(name),
                new Money(new BigDecimal("100.00"), CurrencyCode.BRL),
                active);
    }
}
