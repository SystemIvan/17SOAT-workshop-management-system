package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.listener;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.RetryStockReservationUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.event.StockItemsRestockedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class RestockedStockReservationRetryListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestockedStockReservationRetryListener.class);

    private final ServiceOrderRepository serviceOrderRepository;
    private final RetryStockReservationUseCase retryStockReservationUseCase;

    public RestockedStockReservationRetryListener(
            ServiceOrderRepository serviceOrderRepository,
            RetryStockReservationUseCase retryStockReservationUseCase) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.retryStockReservationUseCase = retryStockReservationUseCase;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StockItemsRestockedEvent event) {
        List<Candidate> candidates = findCandidates(event.stockItemIds());
        for (Candidate candidate : candidates) {
            try {
                retryStockReservationUseCase.execute(candidate.serviceOrderId(), candidate.serviceExecutionId());
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Stock reservation retry failed after receipt {} for service order {} execution {}",
                        event.stockReceiptId(),
                        candidate.serviceOrderId(),
                        candidate.serviceExecutionId());
            }
        }
    }

    private List<Candidate> findCandidates(List<UUID> stockItemIds) {
        Set<UUID> receivedItemIds = Set.copyOf(stockItemIds);
        return serviceOrderRepository.findAwaitingItemsByStockItemIds(receivedItemIds).stream()
                .flatMap(serviceOrder -> serviceOrder.serviceExecutions().stream()
                        .filter(execution -> isCandidate(execution, receivedItemIds))
                        .map(execution -> new Candidate(serviceOrder.id(), serviceOrder.priority(), execution.id())))
                .sorted(Comparator.comparingInt((Candidate candidate) -> priorityRank(candidate.priority()))
                        .thenComparing(Candidate::serviceOrderId)
                        .thenComparing(Candidate::serviceExecutionId))
                .toList();
    }

    private boolean isCandidate(ServiceExecution execution, Set<UUID> receivedItemIds) {
        return execution.status() == ServiceExecutionStatus.AWAITING_ITEMS
                && execution.stockRequirementsFrozen()
                && execution.stockRequirements().stream()
                .map(requirement -> requirement.stockItemId())
                .anyMatch(receivedItemIds::contains);
    }

    private int priorityRank(Priority priority) {
        return switch (priority) {
            case URGENT -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
        };
    }

    private record Candidate(UUID serviceOrderId, Priority priority, UUID serviceExecutionId) {
    }
}
