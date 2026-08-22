package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.event.TechnicianMaterialsReservedEvent;
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

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RetryStockReservationUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final StockReservationApi stockReservationApi;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public RetryStockReservationUseCase(
            ServiceOrderRepository serviceOrderRepository,
            StockReservationApi stockReservationApi,
            ApplicationEventPublisher eventPublisher) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.stockReservationApi = stockReservationApi;
        this.eventPublisher = eventPublisher;
    }

    RetryStockReservationUseCase(
            ServiceOrderRepository serviceOrderRepository,
            StockReservationApi stockReservationApi) {
        this(serviceOrderRepository, stockReservationApi, event -> {
        });
    }

    @Transactional
    public ReserveStockItemsResult execute(UUID serviceOrderId, UUID serviceExecutionId) {
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrowForUpdate(serviceOrderRepository, serviceOrderId);
        ServiceExecution execution = findExecution(serviceOrder, serviceExecutionId);
        validateRetryable(execution);

        ReserveStockItemsResult result = stockReservationApi.reserveAll(List.of(toReservationCommand(execution)))
                .getFirst();
        if (result.outcome() == ReservationAttemptOutcome.RESERVED) {
            serviceOrder.confirmStockReservation(execution.id(), result.reservationId());
            serviceOrderRepository.save(serviceOrder);
            publishTechnicianNotificationIfAssigned(serviceOrder, execution, result);
        }
        return result;
    }

    private ServiceExecution findExecution(ServiceOrder serviceOrder, UUID serviceExecutionId) {
        return serviceOrder.serviceExecutions().stream()
                .filter(execution -> execution.id().equals(serviceExecutionId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "ServiceExecution not found: " + serviceExecutionId));
    }

    private void validateRetryable(ServiceExecution execution) {
        boolean awaitingItems = execution.status() == ServiceExecutionStatus.AWAITING_ITEMS;
        boolean readyWithReservation = execution.status() == ServiceExecutionStatus.READY
                && execution.stockReservationId() != null;
        if (!awaitingItems && !readyWithReservation) {
            throw new IllegalStateException(
                    "Stock reservation retry requires AWAITING_ITEMS or READY with a reservation");
        }
        if (!execution.stockRequirementsFrozen() || execution.stockRequirements().isEmpty()) {
            throw new IllegalStateException("Stock reservation retry requires frozen stock requirements");
        }
    }

    private ReserveStockItemsCommand toReservationCommand(ServiceExecution execution) {
        List<ReserveStockItem> items = execution.stockRequirements().stream()
                .map(requirement -> new ReserveStockItem(requirement.stockItemId(), requirement.quantity()))
                .toList();
        return new ReserveStockItemsCommand(execution.id(), items);
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
