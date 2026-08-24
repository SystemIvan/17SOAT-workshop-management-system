package br.com.fiap.workshop_management_system.registration.servicecatalog.domain.repository;

import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogService;
import br.com.fiap.workshop_management_system.registration.servicecatalog.domain.model.CatalogServiceName;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogServiceRepository {

    Optional<CatalogService> findById(UUID id);

    Optional<CatalogService> findByName(CatalogServiceName name);

    List<CatalogService> findAllActive();

    void save(CatalogService catalogService);
}
