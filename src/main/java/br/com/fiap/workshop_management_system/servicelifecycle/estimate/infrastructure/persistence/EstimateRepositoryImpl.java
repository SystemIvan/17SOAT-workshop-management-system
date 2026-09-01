package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EstimateRepositoryImpl implements EstimateRepository {

    private final EstimateJpaRepository jpaRepository;
    private final EstimatePersistenceMapper mapper;

    public EstimateRepositoryImpl(
            EstimateJpaRepository jpaRepository,
            EstimatePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Estimate> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsByDiagnosisId(UUID diagnosisId) {
        return jpaRepository.existsByDiagnosisId(diagnosisId);
    }

    @Override
    public List<Estimate> findSentExpiredAtOrBefore(Instant now) {
        return jpaRepository
                .findByStatusAndExpiresAtLessThanEqual(
                        EstimateStatus.SENT,
                        now
                )
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void save(Estimate estimate) {
        jpaRepository.saveAndFlush(mapper.toEntity(estimate));
    }
}