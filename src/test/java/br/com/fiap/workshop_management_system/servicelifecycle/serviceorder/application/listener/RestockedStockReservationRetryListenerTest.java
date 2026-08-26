package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.listener;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .RetryStockReservationUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository
        .StockReservationRetryCandidate;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.event.StockItemsRestockedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api
        .ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api
        .ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RestockedStockReservationRetryListenerTest {

    @Test
    void retriesOnlyAffectedFrozenExecutionsInPriorityOrder() {
        UUID receivedStockItemId = UUID.randomUUID();
        ServiceOrder urgent = awaitingOrder(Priority.URGENT, receivedStockItemId, true);
        ServiceOrder high = awaitingOrder(Priority.HIGH, receivedStockItemId, true);
        ServiceOrder normal = awaitingOrder(Priority.NORMAL, receivedStockItemId, true);
        ServiceOrder unaffected = awaitingOrder(Priority.LOW, UUID.randomUUID(), true);
        ServiceOrder unfrozen = awaitingOrder(Priority.LOW, receivedStockItemId, false);
        InMemoryServiceOrderRepository repository = new InMemoryServiceOrderRepository(
                List.of(normal, unaffected, unfrozen, high, urgent));
        RecordingReservationApi reservationApi = new RecordingReservationApi(null);

        listener(repository, reservationApi).on(event(receivedStockItemId));

        assertEquals(List.of(
                urgent.serviceExecutions().getFirst().id(),
                high.serviceExecutions().getFirst().id(),
                normal.serviceExecutions().getFirst().id()), reservationApi.executionIds());
    }

    @Test
    void continuesRetryingOtherCandidatesWhenOneRetryFails() {
        UUID receivedStockItemId = UUID.randomUUID();
        ServiceOrder urgent = awaitingOrder(Priority.URGENT, receivedStockItemId, true);
        ServiceOrder normal = awaitingOrder(Priority.NORMAL, receivedStockItemId, true);
        InMemoryServiceOrderRepository repository = new InMemoryServiceOrderRepository(List.of(urgent, normal));
        RecordingReservationApi reservationApi = new RecordingReservationApi(
                urgent.serviceExecutions().getFirst().id());

        listener(repository, reservationApi).on(event(receivedStockItemId));

        assertEquals(List.of(
                urgent.serviceExecutions().getFirst().id(),
                normal.serviceExecutions().getFirst().id()), reservationApi.executionIds());
    }

    private static RestockedStockReservationRetryListener listener(
            InMemoryServiceOrderRepository repository, RecordingReservationApi reservationApi) {
        return new RestockedStockReservationRetryListener(
                repository,
                new RetryStockReservationUseCase(repository, reservationApi, event -> {
                }));
    }

    private static StockItemsRestockedEvent event(UUID stockItemId) {
        return new StockItemsRestockedEvent(UUID.randomUUID(), UUID.randomUUID(), List.of(stockItemId), Instant.now());
    }

    private static ServiceOrder awaitingOrder(Priority priority, UUID stockItemId, boolean freezeRequirements) {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                priority,
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        UUID diagnosisId = serviceOrder.performDiagnosis(List.of(new DiagnosisItem(
                UUID.randomUUID(),
                "Oil change",
                Money.brl(BigDecimal.TEN),
                List.of(new StockRequirement(
                        stockItemId,
                        StockItemType.PART,
                        2,
                        "Oil filter",
                        Money.brl(BigDecimal.TEN),
                        false)))), UUID.randomUUID(), Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        if (freezeRequirements) {
            serviceOrder.freezeStockRequirements(diagnosisId);
        }
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        return serviceOrder;
    }

    private static final class InMemoryServiceOrderRepository implements ServiceOrderRepository {

        private final Map<UUID, ServiceOrder> byId = new HashMap<>();
        private final List<ServiceOrder> candidates;

        private InMemoryServiceOrderRepository(List<ServiceOrder> candidates) {
            this.candidates = List.copyOf(candidates);
            candidates.forEach(serviceOrder -> byId.put(serviceOrder.id(), serviceOrder));
        }

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<StockReservationRetryCandidate> findAwaitingItemsByStockItemIds(
                java.util.Collection<UUID> stockItemIds) {
            Set<UUID> receivedItemIds = Set.copyOf(stockItemIds);
            return candidates.stream()
                    .flatMap(serviceOrder -> serviceOrder.serviceExecutions().stream()
                            .filter(execution -> execution.status() == ServiceExecutionStatus.AWAITING_ITEMS)
                            .filter(execution -> execution.stockRequirementsFrozen())
                            .filter(execution -> execution.stockRequirements().stream()
                                    .anyMatch(requirement -> receivedItemIds.contains(requirement.stockItemId())))
                            .map(execution -> new StockReservationRetryCandidate(
                                    serviceOrder.id(), serviceOrder.priority(), execution.id())))
                    .toList();
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            byId.put(serviceOrder.id(), serviceOrder);
        }
    }

    private static final class RecordingReservationApi implements StockReservationApi {

        private final List<UUID> executionIds = new ArrayList<>();
        private final UUID failingExecutionId;

        private RecordingReservationApi(UUID failingExecutionId) {
            this.failingExecutionId = failingExecutionId;
        }

        @Override
        public List<ReserveStockItemsResult> reserveAll(List<ReserveStockItemsCommand> commands) {
            ReserveStockItemsCommand command = commands.getFirst();
            executionIds.add(command.serviceExecutionId());
            if (command.serviceExecutionId().equals(failingExecutionId)) {
                throw new IllegalStateException("Concurrent state change");
            }
            return List.of(new ReserveStockItemsResult(
                    command.serviceExecutionId(),
                    ReservationAttemptOutcome.RESERVED,
                    UUID.randomUUID(),
                    true,
                    command.items(),
                    List.of()));
        }

        private List<UUID> executionIds() {
            return List.copyOf(executionIds);
        }
    }
}
