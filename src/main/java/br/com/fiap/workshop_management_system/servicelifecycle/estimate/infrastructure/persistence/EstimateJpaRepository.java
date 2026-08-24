package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EstimateJpaRepository
        extends JpaRepository<EstimateJpaEntity, UUID> {

    boolean existsByDiagnosisId(UUID diagnosisId);

    List<EstimateJpaEntity> findByStatusAndExpiresAtLessThanEqual(
            EstimateStatus status,
            Instant expiresAt
    );
}