package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest.LineDecisionRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.EstimateLineDecision;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecideEstimateLinesUseCaseTest {

    private final VehicleSnapshot vehicleSnapshot = new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015);

    private ServiceOrder diagnosedServiceOrder(String... serviceNames) {
        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        List<DiagnosisItem> items = List.of(serviceNames).stream()
                .map(name -> new DiagnosisItem(UUID.randomUUID(), name, Money.brl(BigDecimal.TEN), List.of()))
                .toList();
        serviceOrder.performDiagnosis(items);
        return serviceOrder;
    }

    private Estimate estimateFor(ServiceOrder serviceOrder) {
        return Estimate.create(
                serviceOrder.id(),
                serviceOrder.openDiagnosisId(),
                serviceOrder.customerId(),
                Instant.parse("2026-08-20T12:00:00Z"),
                Instant.parse("2026-08-22T12:00:00Z"),
                serviceOrder.serviceExecutions().stream()
                        .map(execution -> new br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine(
                                execution.id(), execution.name(), execution.price(), List.of()))
                        .toList());
    }

    @Test
    void approvesASingleLine() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo");
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = estimateFor(serviceOrder);
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, commands -> List.of());

        ServiceOrderResponse response = useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(executionId, EstimateLineDecision.APPROVED))));

        assertEquals(ServiceExecutionStatus.READY, response.executions().get(0).status());
        assertEquals(ServiceExecutionStatus.READY,
                serviceOrders.findById(serviceOrder.id()).orElseThrow().serviceExecutions().get(0).status());
    }

    @Test
    void rejectsASingleLine() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo");
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = estimateFor(serviceOrder);
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, commands -> List.of());

        ServiceOrderResponse response = useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(executionId, EstimateLineDecision.REJECTED))));

        assertEquals(ServiceExecutionStatus.REJECTED, response.executions().get(0).status());
    }

    @Test
    void decidesMultipleLinesInASingleCall() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo", "Alinhamento");
        UUID approvedId = serviceOrder.serviceExecutions().get(0).id();
        UUID rejectedId = serviceOrder.serviceExecutions().get(1).id();
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = estimateFor(serviceOrder);
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, commands -> List.of());

        ServiceOrderResponse response = useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(approvedId, EstimateLineDecision.APPROVED),
                        new LineDecisionRequest(rejectedId, EstimateLineDecision.REJECTED))));

        assertEquals(ServiceExecutionStatus.READY, response.executions().get(0).status());
        assertEquals(ServiceExecutionStatus.REJECTED, response.executions().get(1).status());
    }

    @Test
    void rejectsUnknownEstimate() {
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository();
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository();
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, commands -> List.of());

        assertThrows(NoSuchElementException.class, () -> useCase.execute(UUID.randomUUID(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(UUID.randomUUID(), EstimateLineDecision.APPROVED)))));
    }

    @Test
    void rejectsServiceExecutionThatIsNotPartOfTheEstimateAndAppliesNoDecision() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo", "Alinhamento");
        UUID inEstimateId = serviceOrder.serviceExecutions().get(0).id();
        UUID outsideEstimateId = serviceOrder.serviceExecutions().get(1).id();
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        // Estimate covers only the first execution.
        Estimate estimate = Estimate.create(
                serviceOrder.id(), serviceOrder.openDiagnosisId(), serviceOrder.customerId(),
                Instant.parse("2026-08-20T12:00:00Z"), Instant.parse("2026-08-22T12:00:00Z"),
                List.of(new br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine(
                        inEstimateId, "Troca de óleo", Money.brl(BigDecimal.TEN), List.of())));
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, commands -> List.of());

        assertThrows(NoSuchElementException.class, () -> useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(inEstimateId, EstimateLineDecision.APPROVED),
                        new LineDecisionRequest(outsideEstimateId, EstimateLineDecision.APPROVED)))));

        assertEquals(ServiceExecutionStatus.PENDING,
                serviceOrders.findById(serviceOrder.id()).orElseThrow().serviceExecutions().get(0).status());
    }

    @Test
    void rejectsDuplicateServiceExecutionInTheSameRequest() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo");
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = estimateFor(serviceOrder);
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, commands -> List.of());

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(executionId, EstimateLineDecision.APPROVED),
                        new LineDecisionRequest(executionId, EstimateLineDecision.REJECTED)))));
    }

    @Test
    void rejectsWhenServiceExecutionIsNotPendingAndAppliesNoDecision() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo", "Alinhamento");
        UUID alreadyRejectedId = serviceOrder.serviceExecutions().get(0).id();
        UUID pendingId = serviceOrder.serviceExecutions().get(1).id();
        serviceOrder.rejectExecutionFromEstimate(UUID.randomUUID(), alreadyRejectedId);
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = Estimate.create(
                serviceOrder.id(), UUID.randomUUID(), serviceOrder.customerId(),
                Instant.parse("2026-08-20T12:00:00Z"), Instant.parse("2026-08-22T12:00:00Z"),
                List.of(
                        new br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine(
                                alreadyRejectedId, "Troca de óleo", Money.brl(BigDecimal.TEN), List.of()),
                        new br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine(
                                pendingId, "Alinhamento", Money.brl(BigDecimal.TEN), List.of())));
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, commands -> List.of());

        assertThrows(IllegalStateException.class, () -> useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(pendingId, EstimateLineDecision.APPROVED),
                        new LineDecisionRequest(alreadyRejectedId, EstimateLineDecision.APPROVED)))));

        // save() is only reached after every decision in the request succeeds; a mid-loop failure
        // means nothing from this call is persisted, even though the in-memory ServiceOrder instance
        // (mutated in place, same reference the repository holds) already reflects the earlier decision.
        assertEquals(0, serviceOrders.saveCount);
    }

    @Test
    void reservesApprovedExecutionWithFrozenStockRequirements() {
        UUID stockItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        ServiceOrder serviceOrder = serviceOrderWithStockRequirement(stockItemId);
        UUID diagnosisId = serviceOrder.openDiagnosisId();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrder.freezeStockRequirements(diagnosisId);
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = estimateFor(serviceOrder);
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        List<ReserveStockItemsCommand> receivedCommands = new java.util.ArrayList<>();
        StockReservationApi stockReservationApi = commands -> {
            receivedCommands.addAll(commands);
            ReserveStockItemsCommand command = commands.getFirst();
            return List.of(new ReserveStockItemsResult(
                    command.serviceExecutionId(),
                    ReservationAttemptOutcome.RESERVED,
                    reservationId,
                    true,
                    command.items(),
                    List.of()));
        };
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, stockReservationApi);

        ServiceOrderResponse response = useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(executionId, EstimateLineDecision.APPROVED))));

        assertEquals(ServiceExecutionStatus.READY, response.executions().getFirst().status());
        assertEquals(reservationId, response.executions().getFirst().stockReservationId());
        assertTrue(response.executions().getFirst().stockRequirements().getFirst().reserved());
        assertEquals(1, receivedCommands.size());
        assertEquals(stockItemId, receivedCommands.getFirst().items().getFirst().stockItemId());
        assertEquals(2, receivedCommands.getFirst().items().getFirst().quantity());
    }

    @Test
    void preservesApprovalWhenStockCannotBeReserved() {
        UUID stockItemId = UUID.randomUUID();
        ServiceOrder serviceOrder = serviceOrderWithStockRequirement(stockItemId);
        UUID diagnosisId = serviceOrder.openDiagnosisId();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrder.freezeStockRequirements(diagnosisId);
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = estimateFor(serviceOrder);
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        StockReservationApi stockReservationApi = commands -> {
            ReserveStockItemsCommand command = commands.getFirst();
            return List.of(new ReserveStockItemsResult(
                    command.serviceExecutionId(),
                    ReservationAttemptOutcome.NOT_RESERVED,
                    null,
                    false,
                    command.items(),
                    List.of()));
        };
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, stockReservationApi);

        ServiceOrderResponse response = useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(executionId, EstimateLineDecision.APPROVED))));

        assertEquals(ServiceExecutionStatus.AWAITING_ITEMS, response.executions().getFirst().status());
        assertEquals(null, response.executions().getFirst().stockReservationId());
        assertFalse(response.executions().getFirst().stockRequirements().getFirst().reserved());
        assertTrue(serviceOrder.approvedEstimateIds().contains(estimate.id()));
    }

    @Test
    void keepsIndependentResultsWhenOneApprovedExecutionCannotBeReserved() {
        UUID availableStockItemId = UUID.randomUUID();
        UUID unavailableStockItemId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        ServiceOrder serviceOrder = serviceOrderWithStockRequirements(availableStockItemId, unavailableStockItemId);
        UUID diagnosisId = serviceOrder.openDiagnosisId();
        UUID availableExecutionId = serviceOrder.serviceExecutions().get(0).id();
        UUID unavailableExecutionId = serviceOrder.serviceExecutions().get(1).id();
        serviceOrder.freezeStockRequirements(diagnosisId);
        InMemoryServiceOrderRepository serviceOrders = new InMemoryServiceOrderRepository(serviceOrder);
        Estimate estimate = estimateFor(serviceOrder);
        InMemoryEstimateRepository estimates = new InMemoryEstimateRepository(estimate);
        StockReservationApi stockReservationApi = commands -> commands.stream()
                .map(command -> command.serviceExecutionId().equals(availableExecutionId)
                        ? new ReserveStockItemsResult(
                                command.serviceExecutionId(),
                                ReservationAttemptOutcome.RESERVED,
                                reservationId,
                                true,
                                command.items(),
                                List.of())
                        : new ReserveStockItemsResult(
                                command.serviceExecutionId(),
                                ReservationAttemptOutcome.NOT_RESERVED,
                                null,
                                false,
                                command.items(),
                                List.of()))
                .toList();
        DecideEstimateLinesUseCase useCase = new DecideEstimateLinesUseCase(
                estimates, serviceOrders, stockReservationApi);

        ServiceOrderResponse response = useCase.execute(estimate.id(),
                new DecideEstimateLinesRequest(List.of(
                        new LineDecisionRequest(availableExecutionId, EstimateLineDecision.APPROVED),
                        new LineDecisionRequest(unavailableExecutionId, EstimateLineDecision.APPROVED))));

        assertEquals(ServiceExecutionStatus.READY, response.executions().get(0).status());
        assertEquals(reservationId, response.executions().get(0).stockReservationId());
        assertEquals(ServiceExecutionStatus.AWAITING_ITEMS, response.executions().get(1).status());
        assertEquals(null, response.executions().get(1).stockReservationId());
    }

    private ServiceOrder serviceOrderWithStockRequirement(UUID stockItemId) {
        return serviceOrderWithStockRequirements(stockItemId);
    }

    private ServiceOrder serviceOrderWithStockRequirements(UUID... stockItemIds) {
        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(), vehicleSnapshot);
        List<DiagnosisItem> diagnosisItems = java.util.Arrays.stream(stockItemIds)
                .map(stockItemId -> new DiagnosisItem(
                        UUID.randomUUID(),
                        "Oil change",
                        Money.brl(BigDecimal.TEN),
                        List.of(new StockRequirement(
                                stockItemId,
                                StockItemType.PART,
                                2,
                                "Oil filter",
                                Money.brl(BigDecimal.TEN),
                                false))))
                .toList();
        serviceOrder.performDiagnosis(diagnosisItems);
        return serviceOrder;
    }

    private static final class InMemoryServiceOrderRepository implements ServiceOrderRepository {
        private final Map<UUID, ServiceOrder> byId = new HashMap<>();
        private int saveCount = 0;

        InMemoryServiceOrderRepository(ServiceOrder... orders) {
            for (ServiceOrder order : orders) {
                byId.put(order.id(), order);
            }
        }

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            saveCount++;
            byId.put(serviceOrder.id(), serviceOrder);
        }
    }

    private static final class InMemoryEstimateRepository implements EstimateRepository {
        private final Map<UUID, Estimate> byId = new HashMap<>();

        InMemoryEstimateRepository(Estimate... estimates) {
            for (Estimate estimate : estimates) {
                byId.put(estimate.id(), estimate);
            }
        }

        @Override
        public Optional<Estimate> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean existsByDiagnosisId(UUID diagnosisId) {
            return byId.values().stream().anyMatch(estimate -> estimate.diagnosisId().equals(diagnosisId));
        }

        @Override
        public void save(Estimate estimate) {
            byId.put(estimate.id(), estimate);
        }
    }
}
