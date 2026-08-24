package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogServiceJpaRepository extends JpaRepository<CatalogServiceJpaEntity, UUID> {

    Optional<CatalogServiceJpaEntity> findByNormalizedNameKey(byte[] normalizedNameKey);

    List<CatalogServiceJpaEntity> findAllByActiveTrue();
}
