package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CurrencyCode;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CatalogServiceRepositoryImplTest {

    private CatalogServiceJpaRepository jpaRepository;
    private CatalogServicePersistenceMapper mapper;
    private CatalogServiceConflictLookup conflictLookup;
    private CatalogServiceRepositoryImpl repository;
    private CatalogService catalogService;
    private CatalogServiceJpaEntity entity;

    @BeforeEach
    void setUp() {
        jpaRepository = mock(CatalogServiceJpaRepository.class);
        mapper = mock(CatalogServicePersistenceMapper.class);
        conflictLookup = mock(CatalogServiceConflictLookup.class);
        repository = new CatalogServiceRepositoryImpl(jpaRepository, mapper, conflictLookup);
        catalogService = CatalogService.create(
                new CatalogServiceName("Revisão"),
                new Money(new BigDecimal("100.00"), CurrencyCode.BRL));
        entity = mock(CatalogServiceJpaEntity.class);
        when(mapper.toEntity(catalogService)).thenReturn(entity);
    }

    @Test
    void propagatesUnknownIntegrityViolationWithoutConflictLookup() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "unknown constraint", new IllegalStateException("ck_unrelated_constraint"));
        when(jpaRepository.saveAndFlush(entity)).thenThrow(failure);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> repository.save(catalogService));

        assertSame(failure, thrown);
        verifyNoInteractions(conflictLookup);
    }

    @Test
    void propagatesOriginalNameViolationWhenWinnerCannotBeConfirmed() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "duplicate", new IllegalStateException("uk_catalog_services_active_normalized_name_key"));
        when(jpaRepository.saveAndFlush(entity)).thenThrow(failure);
        when(conflictLookup.findCommittedByName(catalogService.name())).thenReturn(Optional.empty());

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> repository.save(catalogService));

        assertSame(failure, thrown);
        verify(conflictLookup).findCommittedByName(catalogService.name());
    }
}
