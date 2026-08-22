package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest.LineDecisionRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.EstimateLineDecision;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.event.TechnicianMaterialsReservedEvent;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final StockReservationApi stockReservationApi;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public DecideEstimateLinesUseCase(
            EstimateRepository estimateRepository,
            ServiceOrderRepository serviceOrderRepository,
            StockReservationApi stockReservationApi,
            ApplicationEventPublisher eventPublisher) {
        this.estimateRepository = estimateRepository;
        this.serviceOrderRepository = serviceOrderRepository;
        this.stockReservationApi = stockReservationApi;
        this.eventPublisher = eventPublisher;
    }

    DecideEstimateLinesUseCase(
            EstimateRepository estimateRepository,
            ServiceOrderRepository serviceOrderRepository,
            StockReservationApi stockReservationApi) {
        this(estimateRepository, serviceOrderRepository, stockReservationApi, event -> {
        });
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

        ServiceOrder serviceOrder = serviceOrderRepository.findByIdForUpdate(estimate.serviceOrderId())
                .orElseThrow(() -> new NoSuchElementException(
                        "ServiceOrder not found: " + estimate.serviceOrderId()));

        Map<UUID, ServiceExecution> executionsById = serviceOrder.serviceExecutions().stream()
                .collect(Collectors.toMap(ServiceExecution::id, execution -> execution));
        for (UUID requestedId : requestedIds) {
            ServiceExecution execution = executionsById.get(requestedId);
            if (execution == null) {
                throw new NoSuchElementException(
                        "ServiceExecution " + requestedId + " is not part of service order " + serviceOrder.id());
            }
            if (execution.status() != ServiceExecutionStatus.PENDING) {
                throw new IllegalStateException(
                        "ServiceExecution must be PENDING to decide an estimate line: " + requestedId);
            }
        }

        for (LineDecisionRequest decision : request.decisions()) {
            if (decision.decision() == EstimateLineDecision.APPROVED) {
                serviceOrder.authorizeExecutionFromEstimate(estimate.id(), decision.serviceExecutionId());
            } else {
                serviceOrder.rejectExecutionFromEstimate(estimate.id(), decision.serviceExecutionId());
            }
        }

        List<ReserveStockItemsCommand> reservationCommands = request.decisions().stream()
                .filter(decision -> decision.decision() == EstimateLineDecision.APPROVED)
                .map(LineDecisionRequest::serviceExecutionId)
                .map(executionsById::get)
                .filter(execution -> !execution.stockRequirements().isEmpty())
                .peek(this::requireFrozenStockRequirements)
                .map(this::toReservationCommand)
                .toList();
        if (!reservationCommands.isEmpty()) {
            List<ReserveStockItemsResult> reservationResults = stockReservationApi.reserveAll(reservationCommands);
            for (ReserveStockItemsResult result : reservationResults) {
                if (result.outcome() == ReservationAttemptOutcome.RESERVED) {
                    serviceOrder.confirmStockReservation(result.serviceExecutionId(), result.reservationId());
                    ServiceExecution execution = executionsById.get(result.serviceExecutionId());
                    publishTechnicianNotificationIfAssigned(serviceOrder, execution, result);
                }
            }
        }

        serviceOrderRepository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }

    private ReserveStockItemsCommand toReservationCommand(ServiceExecution execution) {
        List<ReserveStockItem> items = execution.stockRequirements().stream()
                .map(requirement -> new ReserveStockItem(requirement.stockItemId(), requirement.quantity()))
                .toList();
        return new ReserveStockItemsCommand(execution.id(), items);
    }

    private void requireFrozenStockRequirements(ServiceExecution execution) {
        if (!execution.stockRequirementsFrozen()) {
            throw new IllegalStateException(
                    "Stock requirements must be frozen before reserving items for execution " + execution.id());
        }
    }

    private void publishTechnicianNotificationIfAssigned(
            ServiceOrder serviceOrder,
            ServiceExecution execution,
            ReserveStockItemsResult result) {
        if (result.newlyCreated() && execution.assignedTechnicianId() != null) {
            eventPublisher.publishEvent(new TechnicianMaterialsReservedEvent(
                    serviceOrder.id(), execution.id(), execution.assignedTechnicianId(), result.reservationId()));
        }
    }
}
