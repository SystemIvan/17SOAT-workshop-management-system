package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest
        .LineDecisionRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.EstimateLineDecision;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalIntendedStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalStatusUpdateRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderStatusLabel;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto
        .ServiceOrderStatusResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase
        .GetServiceOrderStatusUseCase;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrderStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * awaiting-approval-status fix - the real use cases, JPA persistence (H2 + Flyway) and status query agree that a
 * ServiceOrder awaits approval between sending an Estimate and deciding (or expiring) it.
 */
@SpringBootTest
class AwaitingApprovalStatusIntegrationTest {

    @Autowired
    private ServiceOrderRepository serviceOrderRepository;

    @Autowired
    private EstimateRepository estimateRepository;

    @Autowired
    private GenerateEstimateUseCase generateEstimateUseCase;

    @Autowired
    private DecideEstimateLinesUseCase decideEstimateLinesUseCase;

    @Autowired
    private ApplyExternalStatusUpdateUseCase applyExternalStatusUpdateUseCase;

    @Autowired
    private GetServiceOrderStatusUseCase getServiceOrderStatusUseCase;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void generatingTheEstimateMovesTheServiceOrderToAwaitingApproval() {
        ServiceOrder serviceOrder = persistedDiagnosedServiceOrder();
        assertStatus(serviceOrder.id(), ServiceOrderStatus.IN_DIAGNOSIS, ServiceOrderStatusLabel.DIAGNOSTICO);

        generateEstimateUseCase.execute(serviceOrder.id(), serviceOrder.openDiagnosisId());

        assertStatus(serviceOrder.id(), ServiceOrderStatus.AWAITING_APPROVAL,
                ServiceOrderStatusLabel.AGUARDANDO_APROVACAO);
    }

    @Test
    void decidingEveryLineThroughTheInternalChannelLeavesAwaitingApproval() {
        ServiceOrder serviceOrder = persistedDiagnosedServiceOrder();
        Estimate estimate = generateEstimateUseCase.execute(serviceOrder.id(), serviceOrder.openDiagnosisId())
                .estimate();
        UUID executionId = estimate.lines().getFirst().serviceExecutionId();

        decideEstimateLinesUseCase.execute(estimate.id(), new DecideEstimateLinesRequest(List.of(
                new LineDecisionRequest(executionId, EstimateLineDecision.APPROVED))));

        assertStatus(serviceOrder.id(), ServiceOrderStatus.IN_PROGRESS, ServiceOrderStatusLabel.EXECUCAO);
        assertEquals(EstimateStatus.CLOSED, estimateStatus(estimate.id()));
    }

    @Test
    void decidingThroughTheExternalChannelLeavesAwaitingApproval() {
        ServiceOrder serviceOrder = persistedDiagnosedServiceOrder();
        generateEstimateUseCase.execute(serviceOrder.id(), serviceOrder.openDiagnosisId());

        applyExternalStatusUpdateUseCase.execute(
                serviceOrder.id(), new ExternalStatusUpdateRequest(ExternalIntendedStatus.APPROVED));

        assertStatus(serviceOrder.id(), ServiceOrderStatus.IN_PROGRESS, ServiceOrderStatusLabel.EXECUCAO);
    }

    @Test
    void expiringTheEstimateReturnsTheServiceOrderToDiagnosis() {
        ServiceOrder serviceOrder = persistedDiagnosedServiceOrder();
        Estimate estimate = generateEstimateUseCase.execute(serviceOrder.id(), serviceOrder.openDiagnosisId())
                .estimate();
        Instant afterExpiration = estimate.expiresAt().plus(Duration.ofMinutes(1));
        ExpireEstimatesUseCase expireEstimates = new ExpireEstimatesUseCase(
                estimateRepository, serviceOrderRepository, Clock.fixed(afterExpiration, ZoneOffset.UTC));

        inTransaction(expireEstimates::execute);

        assertEquals(EstimateStatus.EXPIRED, estimateStatus(estimate.id()));
        assertStatus(serviceOrder.id(), ServiceOrderStatus.IN_DIAGNOSIS, ServiceOrderStatusLabel.DIAGNOSTICO);
    }

    private ServiceOrder persistedDiagnosedServiceOrder() {
        ServiceOrder serviceOrder = ServiceOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015),
                "Initial assessment");
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(
                List.of(new DiagnosisItem(UUID.randomUUID(), "Troca de óleo", Money.brl(BigDecimal.TEN), List.of())),
                UUID.randomUUID(), Instant.EPOCH);
        inTransaction(() -> serviceOrderRepository.save(serviceOrder));
        return serviceOrder;
    }

    private void assertStatus(UUID serviceOrderId, ServiceOrderStatus status, ServiceOrderStatusLabel label) {
        ServiceOrderStatusResponse response = getServiceOrderStatusUseCase.execute(serviceOrderId);
        assertEquals(status, response.status());
        assertEquals(label, response.statusLabel());
    }

    private EstimateStatus estimateStatus(UUID estimateId) {
        return new TransactionTemplate(transactionManager).execute(
                status -> estimateRepository.findById(estimateId).orElseThrow().status());
    }

    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }
}
