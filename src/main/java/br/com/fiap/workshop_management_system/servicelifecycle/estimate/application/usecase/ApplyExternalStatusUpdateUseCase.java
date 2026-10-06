package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest.LineDecisionRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.EstimateLineDecision;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalIntendedStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalStatusUpdateRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RF40 - applies the customer's intent received from an external channel to every still-pending line of the
 * service order's sent estimate. It only chooses which lines to decide; the decision itself (all-or-nothing,
 * estimate closing, stock reservation) stays in {@link DecideEstimateLinesUseCase}, which re-validates every
 * line under a lock, so a concurrent decision between the lookup here and that call ends in a conflict instead
 * of a partial update.
 */
@Service
public class ApplyExternalStatusUpdateUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final EstimateRepository estimateRepository;
    private final DecideEstimateLinesUseCase decideEstimateLinesUseCase;

    public ApplyExternalStatusUpdateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            DecideEstimateLinesUseCase decideEstimateLinesUseCase) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.estimateRepository = estimateRepository;
        this.decideEstimateLinesUseCase = decideEstimateLinesUseCase;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, ExternalStatusUpdateRequest request) {
        ServiceOrder serviceOrder = serviceOrderRepository.findById(serviceOrderId)
                .orElseThrow(() -> new NoSuchElementException("ServiceOrder not found: " + serviceOrderId));
        Estimate estimate = singleSentEstimate(serviceOrderId);

        Set<UUID> pendingExecutionIds = serviceOrder.serviceExecutions().stream()
                .filter(execution -> execution.status() == ServiceExecutionStatus.PENDING)
                .map(ServiceExecution::id)
                .collect(Collectors.toSet());
        EstimateLineDecision decision = toLineDecision(request.intendedStatus());
        List<LineDecisionRequest> decisions = estimate.lines().stream()
                .map(EstimateLine::serviceExecutionId)
                .filter(pendingExecutionIds::contains)
                .map(executionId -> new LineDecisionRequest(executionId, decision))
                .toList();
        if (decisions.isEmpty()) {
            throw new IllegalStateException(
                    "No pending line to decide in estimate " + estimate.id() + " of service order " + serviceOrderId);
        }

        return decideEstimateLinesUseCase.execute(estimate.id(), new DecideEstimateLinesRequest(decisions));
    }

    private Estimate singleSentEstimate(UUID serviceOrderId) {
        List<Estimate> sent = estimateRepository.findByServiceOrderIdAndStatus(serviceOrderId, EstimateStatus.SENT);
        if (sent.isEmpty()) {
            throw new IllegalStateException("No estimate awaiting decision for service order " + serviceOrderId);
        }
        if (sent.size() > 1) {
            // The external intent only names the service order, so it cannot pick between two open estimates.
            throw new IllegalStateException(
                    "More than one estimate awaiting decision for service order " + serviceOrderId);
        }
        return sent.get(0);
    }

    private static EstimateLineDecision toLineDecision(ExternalIntendedStatus intendedStatus) {
        return switch (intendedStatus) {
            case APPROVED -> EstimateLineDecision.APPROVED;
            case REJECTED -> EstimateLineDecision.REJECTED;
        };
    }
}
