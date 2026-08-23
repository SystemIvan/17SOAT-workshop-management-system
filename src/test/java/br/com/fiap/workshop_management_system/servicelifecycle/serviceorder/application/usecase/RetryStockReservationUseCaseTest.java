package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.event.TechnicianMaterialsReservedEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RetryStockReservationUseCaseTest {

    @Test
    void reservesTheFrozenRequirementsOfAnAwaitingExecution() {
        ServiceOrder serviceOrder = frozenAndAuthorizedServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        UUID reservationId = UUID.randomUUID();
        InMemoryServiceOrderRepository repository = new InMemoryServiceOrderRepository(serviceOrder);
        List<ReserveStockItemsCommand> receivedCommands = new ArrayList<>();
        StockReservationApi reservationApi = commands -> {
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
        RetryStockReservationUseCase useCase = new RetryStockReservationUseCase(repository, reservationApi);

        ReserveStockItemsResult result = useCase.execute(serviceOrder.id(), executionId);

        assertEquals(ReservationAttemptOutcome.RESERVED, result.outcome());
        assertEquals(reservationId, serviceOrder.serviceExecutions().getFirst().stockReservationId());
        assertEquals(ServiceExecutionStatus.READY, serviceOrder.serviceExecutions().getFirst().status());
        assertTrue(serviceOrder.serviceExecutions().getFirst().stockRequirements().getFirst().reserved());
        assertEquals(1, receivedCommands.size());
        assertEquals(2, receivedCommands.getFirst().items().getFirst().quantity());
    }

    @Test
    void returnsTheExistingReservationForAnAlreadyReadyExecution() {
        ServiceOrder serviceOrder = frozenAndAuthorizedServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        UUID reservationId = UUID.randomUUID();
        serviceOrder.confirmStockReservation(executionId, reservationId);
        InMemoryServiceOrderRepository repository = new InMemoryServiceOrderRepository(serviceOrder);
        StockReservationApi reservationApi = commands -> {
            ReserveStockItemsCommand command = commands.getFirst();
            return List.of(new ReserveStockItemsResult(
                    command.serviceExecutionId(),
                    ReservationAttemptOutcome.RESERVED,
                    reservationId,
                    false,
                    command.items(),
                    List.of()));
        };
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        RetryStockReservationUseCase useCase = new RetryStockReservationUseCase(
                repository, reservationApi, eventPublisher);

        ReserveStockItemsResult result = useCase.execute(serviceOrder.id(), executionId);

        assertEquals(ReservationAttemptOutcome.RESERVED, result.outcome());
        assertFalse(result.newlyCreated());
        assertEquals(reservationId, serviceOrder.serviceExecutions().getFirst().stockReservationId());
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preservesAwaitingItemsWhenTheRetryCannotReserveStock() {
        ServiceOrder serviceOrder = frozenAndAuthorizedServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        InMemoryServiceOrderRepository repository = new InMemoryServiceOrderRepository(serviceOrder);
        StockReservationApi reservationApi = commands -> {
            ReserveStockItemsCommand command = commands.getFirst();
            return List.of(new ReserveStockItemsResult(
                    command.serviceExecutionId(),
                    ReservationAttemptOutcome.NOT_RESERVED,
                    null,
                    false,
                    command.items(),
                    List.of()));
        };
        RetryStockReservationUseCase useCase = new RetryStockReservationUseCase(repository, reservationApi);

        ReserveStockItemsResult result = useCase.execute(serviceOrder.id(), executionId);

        assertEquals(ReservationAttemptOutcome.NOT_RESERVED, result.outcome());
        assertEquals(ServiceExecutionStatus.AWAITING_ITEMS, serviceOrder.serviceExecutions().getFirst().status());
        assertEquals(null, serviceOrder.serviceExecutions().getFirst().stockReservationId());
    }

    @Test
    void rejectsAStateThatCannotBeRetriedWithoutCallingTheReservationApi() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(List.of(new DiagnosisItem(
                UUID.randomUUID(),
                "Oil change",
                Money.brl(BigDecimal.TEN),
                List.of())), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        InMemoryServiceOrderRepository repository = new InMemoryServiceOrderRepository(serviceOrder);
        AtomicBoolean apiCalled = new AtomicBoolean(false);
        StockReservationApi reservationApi = commands -> {
            apiCalled.set(true);
            return List.of();
        };
        RetryStockReservationUseCase useCase = new RetryStockReservationUseCase(repository, reservationApi);

        assertThrows(IllegalStateException.class, () -> useCase.execute(serviceOrder.id(), executionId));
        assertFalse(apiCalled.get());
    }

    @Test
    void publishesNotificationForAnAssignedTechnicianOnlyWhenTheReservationIsNew() {
        ServiceOrder serviceOrder = frozenAndAuthorizedServiceOrder();
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        UUID technicianId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        serviceOrder.confirmTechnicianAssignment(executionId, technicianId);
        InMemoryServiceOrderRepository repository = new InMemoryServiceOrderRepository(serviceOrder);
        StockReservationApi reservationApi = commands -> {
            ReserveStockItemsCommand command = commands.getFirst();
            return List.of(new ReserveStockItemsResult(
                    command.serviceExecutionId(),
                    ReservationAttemptOutcome.RESERVED,
                    reservationId,
                    true,
                    command.items(),
                    List.of()));
        };
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        RetryStockReservationUseCase useCase = new RetryStockReservationUseCase(
                repository, reservationApi, eventPublisher);

        useCase.execute(serviceOrder.id(), executionId);

        verify(eventPublisher).publishEvent(new TechnicianMaterialsReservedEvent(
                serviceOrder.id(), executionId, technicianId, reservationId));
    }

    private ServiceOrder frozenAndAuthorizedServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
        StockRequirement requirement = new StockRequirement(
                UUID.randomUUID(),
                StockItemType.PART,
                2,
                "Oil filter",
                Money.brl(BigDecimal.TEN),
                false);
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        UUID diagnosisId = serviceOrder.performDiagnosis(List.of(new DiagnosisItem(
                UUID.randomUUID(),
                "Oil change",
                Money.brl(BigDecimal.TEN),
                List.of(requirement))), UUID.randomUUID(), java.time.Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().getFirst().id();
        serviceOrder.freezeStockRequirements(diagnosisId);
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        return serviceOrder;
    }

    private static final class InMemoryServiceOrderRepository implements ServiceOrderRepository {

        private final Map<UUID, ServiceOrder> byId = new HashMap<>();

        private InMemoryServiceOrderRepository(ServiceOrder... serviceOrders) {
            for (ServiceOrder serviceOrder : serviceOrders) {
                byId.put(serviceOrder.id(), serviceOrder);
            }
        }

        @Override
        public Optional<ServiceOrder> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public void save(ServiceOrder serviceOrder) {
            byId.put(serviceOrder.id(), serviceOrder);
        }
    }
}
