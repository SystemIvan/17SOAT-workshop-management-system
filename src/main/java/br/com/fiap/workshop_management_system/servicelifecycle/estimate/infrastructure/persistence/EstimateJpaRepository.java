package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EstimateJpaRepository
        extends JpaRepository<EstimateJpaEntity, UUID> {

    boolean existsByDiagnosisId(UUID diagnosisId);
}