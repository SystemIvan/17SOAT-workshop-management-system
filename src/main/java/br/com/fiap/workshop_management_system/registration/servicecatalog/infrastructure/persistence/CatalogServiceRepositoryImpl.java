package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.servicecatalog.application.exception
        .CatalogServiceNameAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository.CatalogServiceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CatalogServiceRepositoryImpl implements CatalogServiceRepository {

    private static final String NAME_CONSTRAINT = "uk_catalog_services_active_normalized_name_key";

    private final CatalogServiceJpaRepository jpaRepository;
    private final CatalogServicePersistenceMapper mapper;
    private final CatalogServiceConflictLookup conflictLookup;

    public CatalogServiceRepositoryImpl(
            CatalogServiceJpaRepository jpaRepository,
            CatalogServicePersistenceMapper mapper,
            CatalogServiceConflictLookup conflictLookup) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.conflictLookup = conflictLookup;
    }

    @Override
    public Optional<CatalogService> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<CatalogService> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Optional<CatalogService> findActiveByName(CatalogServiceName name) {
        return jpaRepository.findByActiveTrueAndNormalizedNameKey(mapper.normalizedNameKey(name))
                .map(mapper::toDomain);
    }

    @Override
    public List<CatalogService> findAllActive() {
        return jpaRepository.findAllByActiveTrue().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(CatalogService catalogService) {
        try {
            jpaRepository.saveAndFlush(mapper.toEntity(catalogService));
        } catch (DataIntegrityViolationException exception) {
            if (!isNameUniquenessViolation(exception)) {
                throw exception;
            }

            CatalogService existing = conflictLookup.findCommittedByName(catalogService.name()).orElseThrow(
                    () -> exception);
            throw new CatalogServiceNameAlreadyExistsException(existing.id(), existing.name().value());
        }
    }

    private static boolean isNameUniquenessViolation(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains(NAME_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
