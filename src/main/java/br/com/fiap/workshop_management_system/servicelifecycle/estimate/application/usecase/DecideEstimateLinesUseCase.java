package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest.LineDecisionRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.EstimateLineDecision;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RF15 - decidir (aprovar/rejeitar) uma ou mais linhas de uma Estimate já gerada. A decisão é
 * aplicada na ServiceOrder (fonte de verdade), não na Estimate - ver nota sobre AD-008 na
 * functional-spec.
 */
@Service
public class DecideEstimateLinesUseCase {

    private final EstimateRepository estimateRepository;
    private final ServiceOrderRepository serviceOrderRepository;

    public DecideEstimateLinesUseCase(
            EstimateRepository estimateRepository, ServiceOrderRepository serviceOrderRepository) {
        this.estimateRepository = estimateRepository;
        this.serviceOrderRepository = serviceOrderRepository;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID estimateId, DecideEstimateLinesRequest request) {
        Estimate estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new NoSuchElementException("Estimate not found: " + estimateId));

        Set<UUID> requestedIds = new HashSet<>();
        for (LineDecisionRequest decision : request.decisions()) {
            if (!requestedIds.add(decision.serviceExecutionId())) {
                throw new IllegalArgumentException(
                        "Duplicate serviceExecutionId in the same request: " + decision.serviceExecutionId());
            }
        }

        Set<UUID> lineIds = estimate.lines().stream()
                .map(EstimateLine::serviceExecutionId)
                .collect(Collectors.toSet());
        for (UUID requestedId : requestedIds) {
            if (!lineIds.contains(requestedId)) {
                throw new NoSuchElementException(
                        "ServiceExecution " + requestedId + " is not part of estimate " + estimateId);
            }
        }

        ServiceOrder serviceOrder = serviceOrderRepository.findById(estimate.serviceOrderId())
                .orElseThrow(() -> new NoSuchElementException(
                        "ServiceOrder not found: " + estimate.serviceOrderId()));

        for (LineDecisionRequest decision : request.decisions()) {
            if (decision.decision() == EstimateLineDecision.APPROVED) {
                serviceOrder.authorizeExecutionFromEstimate(estimate.id(), decision.serviceExecutionId());
            } else {
                serviceOrder.rejectExecutionFromEstimate(estimate.id(), decision.serviceExecutionId());
            }
        }

        serviceOrderRepository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }
}
