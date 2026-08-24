package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class CatalogServiceConflictLookup {

    private final CatalogServiceJpaRepository jpaRepository;
    private final CatalogServicePersistenceMapper mapper;

    public CatalogServiceConflictLookup(
            CatalogServiceJpaRepository jpaRepository,
            CatalogServicePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<CatalogService> findCommittedByName(CatalogServiceName name) {
        return jpaRepository.findByNormalizedNameKey(mapper.normalizedNameKey(name)).map(mapper::toDomain);
    }
}
