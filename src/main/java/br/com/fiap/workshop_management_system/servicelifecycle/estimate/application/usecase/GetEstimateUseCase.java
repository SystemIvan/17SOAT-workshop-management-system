package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GetEstimateUseCase {

    private final EstimateRepository estimateRepository;

    public GetEstimateUseCase(EstimateRepository estimateRepository) {
        this.estimateRepository = estimateRepository;
    }

    @Transactional(readOnly = true)
    public Estimate execute(UUID estimateId) {
        return estimateRepository.findById(estimateId)
                .orElseThrow(() ->
                        new NoSuchElementException("Estimate not found: " + estimateId));
    }
}