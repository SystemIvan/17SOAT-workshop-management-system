package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class ExpireEstimatesUseCase {

    private final EstimateRepository estimateRepository;
    private final Clock clock;

    @Autowired
    public ExpireEstimatesUseCase(EstimateRepository estimateRepository) {
        this(estimateRepository, Clock.systemUTC());
    }

    ExpireEstimatesUseCase(
            EstimateRepository estimateRepository,
            Clock clock) {
        this.estimateRepository = estimateRepository;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        Instant now = clock.instant();

        List<Estimate> expiredCandidates =
                estimateRepository.findSentExpiredAtOrBefore(now);

        for (Estimate estimate : expiredCandidates) {
            estimate.expire();
            estimateRepository.save(estimate);
        }

        return expiredCandidates.size();
    }
}