package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstimateRepository {

    Optional<Estimate> findById(UUID id);

    boolean existsByDiagnosisId(UUID diagnosisId);

    List<Estimate> findSentExpiredAtOrBefore(Instant now);

    void save(Estimate estimate);
}