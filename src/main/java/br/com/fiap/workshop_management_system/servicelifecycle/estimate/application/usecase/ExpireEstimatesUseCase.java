package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ExpireEstimatesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpireEstimatesUseCase.class);

    private final EstimateRepository estimateRepository;
    private final ServiceOrderRepository serviceOrderRepository;
    private final Clock clock;

    @Autowired
    public ExpireEstimatesUseCase(EstimateRepository estimateRepository, ServiceOrderRepository serviceOrderRepository) {
        this(estimateRepository, serviceOrderRepository, Clock.systemUTC());
    }

    ExpireEstimatesUseCase(
            EstimateRepository estimateRepository,
            ServiceOrderRepository serviceOrderRepository,
            Clock clock) {
        this.estimateRepository = estimateRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.clock = clock;
    }

    @Transactional
    public int execute() {
        Instant now = clock.instant();

        List<Estimate> expiredCandidates =
                estimateRepository.findSentExpiredAtOrBefore(now);

        Set<UUID> affectedServiceOrderIds = new LinkedHashSet<>();
        Set<UUID> expiredEstimateIds = new LinkedHashSet<>();
        for (Estimate estimate : expiredCandidates) {
            estimate.expire();
            estimateRepository.save(estimate);
            affectedServiceOrderIds.add(estimate.serviceOrderId());
            expiredEstimateIds.add(estimate.id());
        }

        // Once per ServiceOrder, even when several of its Estimates expire in the same run
        // (awaiting-approval-status: an expired Estimate returns the order to diagnosis).
        affectedServiceOrderIds.forEach(serviceOrderId -> leaveAwaitingApproval(serviceOrderId, expiredEstimateIds));

        return expiredCandidates.size();
    }

    private void leaveAwaitingApproval(UUID serviceOrderId, Set<UUID> expiredEstimateIds) {
        // Expired ones are filtered by id instead of relying on their new status having been flushed.
        boolean anotherSentEstimate = estimateRepository
                .findByServiceOrderIdAndStatus(serviceOrderId, EstimateStatus.SENT).stream()
                .anyMatch(other -> !expiredEstimateIds.contains(other.id()));
        if (anotherSentEstimate) {
            return;
        }
        serviceOrderRepository.findByIdForUpdate(serviceOrderId).ifPresentOrElse(
                serviceOrder -> {
                    serviceOrder.markSentEstimateExpired();
                    serviceOrderRepository.save(serviceOrder);
                },
                () -> log.warn("Expired estimate references missing service order {}", serviceOrderId));
    }
}
