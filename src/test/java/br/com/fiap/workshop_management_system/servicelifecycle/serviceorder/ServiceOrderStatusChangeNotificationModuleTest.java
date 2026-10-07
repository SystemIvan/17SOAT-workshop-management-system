package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalIntendedStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalStatusUpdateRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase
        .ApplyExternalStatusUpdateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase.GenerateEstimateUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.notification.application.port
        .CustomerEstimateNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto
        .FinalizeServiceOrderRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerNotificationPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .CompleteExecutionUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .FinalizeServiceOrderUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase.StartExecutionUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.event
        .StockItemsRestockedEvent;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api
        .ReservationAttemptOutcome;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api
        .ReserveStockItemsResult;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.api.StockReservationApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailSendException;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF52 - end-to-end status-change notifications through the real pipeline: use case transaction ->
 * ServiceOrderRepository.save publishes ServiceOrderStatusChanged -> after commit,
 * ServiceOrderStatusChangedNotificationListener (async) -> CustomerNotificationPort. Only the outbound ports (and the
 * stock reservation API, for the restock path) are mocked.
 */
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
class ServiceOrderStatusChangeNotificationModuleTest {

    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);
    private static final long QUIET_PERIOD_MS = 750;

    @MockitoBean
    private CustomerNotificationPort statusPort;

    @MockitoBean
    private CustomerEstimateNotificationPort estimatePort;

    @MockitoBean
    private StockReservationApi stockReservationApi;

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private GenerateEstimateUseCase generateEstimateUseCase;

    @Autowired
    private ApplyExternalStatusUpdateUseCase applyExternalStatusUpdateUseCase;

    @Autowired
    private StartExecutionUseCase startExecutionUseCase;

    @Autowired
    private CompleteExecutionUseCase completeExecutionUseCase;

    @Autowired
    private FinalizeServiceOrderUseCase finalizeServiceOrderUseCase;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void notifiesEachNominalTransitionExactlyOnceAcrossTheWholeLifecycle() {
        ServiceOrder serviceOrder = diagnosedServiceOrder(List.of());
        UUID id = serviceOrder.id();
        UUID customerId = serviceOrder.customerId();
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        expectNotification(id, customerId, ServiceOrderStatusLabel.DIAGNOSTICO);

        // Diagnóstico -> Aguardando Aprovação, plus the separate estimate e-mail.
        generateEstimateUseCase.execute(id, serviceOrder.openDiagnosisId());
        expectNotification(id, customerId, ServiceOrderStatusLabel.AGUARDANDO_APROVACAO);
        verify(estimatePort, timeout(ASYNC_TIMEOUT.toMillis()))
                .notifyEstimateGenerated(any(), eq(id), eq(customerId), any());

        // Customer approval through the RF40 external channel -> Execução.
        applyExternalStatusUpdateUseCase.execute(id, new ExternalStatusUpdateRequest(ExternalIntendedStatus.APPROVED));
        expectNotification(id, customerId, ServiceOrderStatusLabel.EXECUCAO);

        // Starting the execution keeps "Execução": no e-mail.
        update(id, loaded -> loaded.confirmTechnicianAssignment(executionId, UUID.randomUUID()));
        startExecutionUseCase.execute(id, executionId);

        completeExecutionUseCase.execute(id, executionId);
        expectNotification(id, customerId, ServiceOrderStatusLabel.FINALIZADA);

        // The former RF33 "finalized" notice is now this single "Entregue" e-mail.
        finalizeServiceOrderUseCase.execute(id, new FinalizeServiceOrderRequest(true));
        expectNotification(id, customerId, ServiceOrderStatusLabel.ENTREGUE);

        verify(statusPort, after(QUIET_PERIOD_MS).times(5)).notifyServiceOrderStatusChanged(eq(id), any(), any());
        verify(estimatePort, after(QUIET_PERIOD_MS).times(1)).notifyEstimateGenerated(any(), eq(id), any(), any());
    }

    @Test
    void aRejectedCommandSendsNoEmail() {
        ServiceOrder serviceOrder = diagnosedServiceOrder(List.of());
        expectNotification(serviceOrder.id(), serviceOrder.customerId(), ServiceOrderStatusLabel.DIAGNOSTICO);

        assertThrows(IllegalStateException.class, () -> finalizeServiceOrderUseCase.execute(
                serviceOrder.id(), new FinalizeServiceOrderRequest(true)));

        verify(statusPort, after(QUIET_PERIOD_MS).times(1))
                .notifyServiceOrderStatusChanged(eq(serviceOrder.id()), any(), any());
    }

    @Test
    void aDeliveryFailureDoesNotRollBackTheStatusChange() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(statusPort).notifyServiceOrderStatusChanged(any(), any(), any());

        ServiceOrder serviceOrder = diagnosedServiceOrder(List.of());

        expectNotification(serviceOrder.id(), serviceOrder.customerId(), ServiceOrderStatusLabel.DIAGNOSTICO);
        assertEquals(ServiceOrderStatus.IN_DIAGNOSIS, persistedStatus(serviceOrder.id()));
    }

    @Test
    void restockMovingAwaitingItemsToInProgressSendsNoEmail() {
        UUID stockItemId = UUID.randomUUID();
        StockRequirement part = new StockRequirement(
                stockItemId, StockItemType.PART, 1, "Filtro de óleo", Money.brl(BigDecimal.TEN), false);
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        UUID diagnosisId = serviceOrder.performDiagnosis(List.of(diagnosisItem(List.of(part))), UUID.randomUUID(),
                Instant.EPOCH);
        UUID executionId = serviceOrder.serviceExecutions().get(0).id();
        serviceOrder.freezeStockRequirements(diagnosisId);
        serviceOrder.authorizeExecutionFromEstimate(UUID.randomUUID(), executionId);
        assertEquals(ServiceOrderStatus.AWAITING_ITEMS, serviceOrder.status());
        inTransaction(() -> serviceOrderRepository.save(serviceOrder));
        expectNotification(serviceOrder.id(), serviceOrder.customerId(), ServiceOrderStatusLabel.EXECUCAO);

        when(stockReservationApi.reserveAll(anyList())).thenReturn(List.of(new ReserveStockItemsResult(
                executionId, ReservationAttemptOutcome.RESERVED, UUID.randomUUID(), false, List.of(), List.of())));
        inTransaction(() -> eventPublisher.publishEvent(new StockItemsRestockedEvent(
                UUID.randomUUID(), UUID.randomUUID(), List.of(stockItemId), Instant.now())));

        await().atMost(ASYNC_TIMEOUT)
                .until(() -> persistedStatus(serviceOrder.id()) == ServiceOrderStatus.IN_PROGRESS);
        verify(statusPort, after(QUIET_PERIOD_MS).times(1))
                .notifyServiceOrderStatusChanged(eq(serviceOrder.id()), any(), any());
    }

    private ServiceOrder diagnosedServiceOrder(List<StockRequirement> stockRequirements) {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(List.of(diagnosisItem(stockRequirements)), UUID.randomUUID(), Instant.EPOCH);
        inTransaction(() -> serviceOrderRepository.save(serviceOrder));
        return serviceOrder;
    }

    private DiagnosisItem diagnosisItem(List<StockRequirement> stockRequirements) {
        return new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), stockRequirements);
    }

    private void expectNotification(UUID serviceOrderId, UUID customerId, ServiceOrderStatusLabel label) {
        verify(statusPort, timeout(ASYNC_TIMEOUT.toMillis()).times(1))
                .notifyServiceOrderStatusChanged(serviceOrderId, customerId, label);
    }

    private void update(UUID serviceOrderId, Consumer<ServiceOrder> change) {
        inTransaction(() -> {
            ServiceOrder loaded = serviceOrderRepository.findById(serviceOrderId).orElseThrow();
            change.accept(loaded);
            serviceOrderRepository.save(loaded);
        });
    }

    private ServiceOrderStatus persistedStatus(UUID serviceOrderId) {
        return new TransactionTemplate(transactionManager).execute(
                status -> serviceOrderRepository.findById(serviceOrderId).orElseThrow().status());
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }
}
