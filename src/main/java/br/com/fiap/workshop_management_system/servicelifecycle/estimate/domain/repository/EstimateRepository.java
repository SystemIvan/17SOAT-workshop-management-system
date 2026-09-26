package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EstimateRepository {

    Optional<Estimate> findById(UUID id);

    boolean existsByDiagnosisId(UUID diagnosisId);

    List<Estimate> findSentExpiredAtOrBefore(Instant now);

    /**
     * Default implementation throws so the existing in-memory test fakes of this interface, none of which
     * look estimates up by service order, don't need to implement it (same approach as
     * {@code ServiceOrderRepository.search}).
     */
    default List<Estimate> findByServiceOrderIdAndStatus(UUID serviceOrderId, EstimateStatus status) {
        throw new UnsupportedOperationException(
                "lookup by service order not supported by this EstimateRepository");
    }

    void save(Estimate estimate);
}