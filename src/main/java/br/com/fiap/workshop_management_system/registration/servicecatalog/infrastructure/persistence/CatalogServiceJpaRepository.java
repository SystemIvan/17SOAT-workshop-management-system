package br.com.fiap.workshop_management_system.registration.servicecatalog.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogServiceJpaRepository extends JpaRepository<CatalogServiceJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select service from CatalogServiceJpaEntity service where service.id = :id")
    Optional<CatalogServiceJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<CatalogServiceJpaEntity> findByActiveTrueAndNormalizedNameKey(byte[] normalizedNameKey);

    List<CatalogServiceJpaEntity> findAllByActiveTrue();
}
