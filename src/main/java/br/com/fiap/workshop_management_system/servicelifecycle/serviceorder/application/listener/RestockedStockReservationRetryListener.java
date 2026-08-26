package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.listener;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .RetryStockReservationUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository
        .StockReservationRetryCandidate;
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
        List<StockReservationRetryCandidate> candidates = findCandidates(event.stockItemIds());
        for (StockReservationRetryCandidate candidate : candidates) {
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

    private List<StockReservationRetryCandidate> findCandidates(List<UUID> stockItemIds) {
        Set<UUID> receivedItemIds = Set.copyOf(stockItemIds);
        return serviceOrderRepository.findAwaitingItemsByStockItemIds(receivedItemIds).stream()
                .sorted(Comparator.comparingInt(
                                (StockReservationRetryCandidate candidate) -> priorityRank(candidate.priority()))
                        .thenComparing(StockReservationRetryCandidate::serviceOrderId)
                        .thenComparing(StockReservationRetryCandidate::serviceExecutionId))
                .toList();
    }

    private int priorityRank(Priority priority) {
        return switch (priority) {
            case URGENT -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
        };
    }
}
