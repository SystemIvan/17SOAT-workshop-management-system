package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;

import java.util.Optional;
import java.util.UUID;

public interface EstimateRepository {

    Optional<Estimate> findById(UUID id);

    boolean existsByDiagnosisId(UUID diagnosisId);

    void save(Estimate estimate);
}