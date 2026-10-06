package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.DecideEstimateLinesRequest.LineDecisionRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.EstimateLineDecision;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalIntendedStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto.ExternalStatusUpdateRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApplyExternalStatusUpdateUseCaseTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-26T12:00:00Z");

    private final ServiceOrderRepository serviceOrderRepository = mock(ServiceOrderRepository.class);
    private final EstimateRepository estimateRepository = mock(EstimateRepository.class);
    private final DecideEstimateLinesUseCase decideEstimateLinesUseCase = mock(DecideEstimateLinesUseCase.class);
    private final ApplyExternalStatusUpdateUseCase useCase = new ApplyExternalStatusUpdateUseCase(
            serviceOrderRepository, estimateRepository, decideEstimateLinesUseCase);

    @Test
    void approvedIntentApprovesEveryPendingLineOfTheSentEstimate() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo", "Alinhamento");
        Estimate estimate = sentEstimateFor(serviceOrder);
        ServiceOrderResponse expected = stubLookups(serviceOrder, List.of(estimate));

        ServiceOrderResponse response = useCase.execute(
                serviceOrder.id(), new ExternalStatusUpdateRequest(ExternalIntendedStatus.APPROVED));

        assertSame(expected, response);
        DecideEstimateLinesRequest request = capturedRequest(estimate.id());
        assertEquals(List.of(
                new LineDecisionRequest(executionId(serviceOrder, 0), EstimateLineDecision.APPROVED),
                new LineDecisionRequest(executionId(serviceOrder, 1), EstimateLineDecision.APPROVED)),
                request.decisions());
    }

    @Test
    void rejectedIntentRejectsEveryPendingLine() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo");
        Estimate estimate = sentEstimateFor(serviceOrder);
        stubLookups(serviceOrder, List.of(estimate));

        useCase.execute(serviceOrder.id(), new ExternalStatusUpdateRequest(ExternalIntendedStatus.REJECTED));

        assertEquals(
                List.of(new LineDecisionRequest(executionId(serviceOrder, 0), EstimateLineDecision.REJECTED)),
                capturedRequest(estimate.id()).decisions());
    }

    @Test
    void linesAlreadyDecidedAreLeftOutOfTheRequest() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo", "Alinhamento");
        Estimate estimate = sentEstimateFor(serviceOrder);
        serviceOrder.rejectExecutionFromEstimate(estimate.id(), executionId(serviceOrder, 0));
        stubLookups(serviceOrder, List.of(estimate));

        useCase.execute(serviceOrder.id(), new ExternalStatusUpdateRequest(ExternalIntendedStatus.APPROVED));

        assertEquals(
                List.of(new LineDecisionRequest(executionId(serviceOrder, 1), EstimateLineDecision.APPROVED)),
                capturedRequest(estimate.id()).decisions());
    }

    @Test
    void unknownServiceOrderIsNotFound() {
        UUID serviceOrderId = UUID.randomUUID();
        when(serviceOrderRepository.findById(serviceOrderId)).thenReturn(Optional.empty());
        ExternalStatusUpdateRequest request = new ExternalStatusUpdateRequest(ExternalIntendedStatus.APPROVED);

        assertThrows(NoSuchElementException.class, () -> useCase.execute(serviceOrderId, request));

        verifyNoInteractions(estimateRepository, decideEstimateLinesUseCase);
    }

    @Test
    void serviceOrderWithoutASentEstimateIsAConflict() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo");
        stubLookups(serviceOrder, List.of());

        assertConflictWithoutDelegating(serviceOrder);
    }

    @Test
    void serviceOrderWithTwoSentEstimatesIsAConflict() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo");
        stubLookups(serviceOrder, List.of(sentEstimateFor(serviceOrder), sentEstimateFor(serviceOrder)));

        assertConflictWithoutDelegating(serviceOrder);
    }

    @Test
    void sentEstimateWithoutPendingLinesIsAConflict() {
        ServiceOrder serviceOrder = diagnosedServiceOrder("Troca de óleo");
        Estimate estimate = sentEstimateFor(serviceOrder);
        serviceOrder.rejectExecutionFromEstimate(estimate.id(), executionId(serviceOrder, 0));
        stubLookups(serviceOrder, List.of(estimate));

        assertConflictWithoutDelegating(serviceOrder);
    }

    private void assertConflictWithoutDelegating(ServiceOrder serviceOrder) {
        ExternalStatusUpdateRequest request = new ExternalStatusUpdateRequest(ExternalIntendedStatus.APPROVED);

        assertThrows(IllegalStateException.class, () -> useCase.execute(serviceOrder.id(), request));

        verifyNoInteractions(decideEstimateLinesUseCase);
    }

    private ServiceOrderResponse stubLookups(ServiceOrder serviceOrder, List<Estimate> sentEstimates) {
        ServiceOrderResponse response = mock(ServiceOrderResponse.class);
        when(serviceOrderRepository.findById(serviceOrder.id())).thenReturn(Optional.of(serviceOrder));
        when(estimateRepository.findByServiceOrderIdAndStatus(serviceOrder.id(), EstimateStatus.SENT))
                .thenReturn(sentEstimates);
        when(decideEstimateLinesUseCase.execute(any(), any())).thenReturn(response);
        return response;
    }

    private DecideEstimateLinesRequest capturedRequest(UUID estimateId) {
        ArgumentCaptor<DecideEstimateLinesRequest> captor = ArgumentCaptor.forClass(DecideEstimateLinesRequest.class);
        verify(decideEstimateLinesUseCase).execute(eq(estimateId), captor.capture());
        return captor.getValue();
    }

    private static ServiceOrder diagnosedServiceOrder(String... serviceNames) {
        ServiceOrder serviceOrder = ServiceOrder.create(UUID.randomUUID(), UUID.randomUUID(),
                new VehicleSnapshot("ABC1D23", "Fiat", "Uno", 2015), "Initial assessment");
        List<DiagnosisItem> items = List.of(serviceNames).stream()
                .map(name -> new DiagnosisItem(UUID.randomUUID(), name, Money.brl(BigDecimal.TEN), List.of()))
                .toList();
        serviceOrder.assignDiagnosisAssignee(UUID.randomUUID());
        serviceOrder.performDiagnosis(items, UUID.randomUUID(), Instant.EPOCH);
        return serviceOrder;
    }

    private static Estimate sentEstimateFor(ServiceOrder serviceOrder) {
        List<EstimateLine> lines = serviceOrder.serviceExecutions().stream()
                .map(execution -> new EstimateLine(execution.id(), execution.name(), execution.price(), List.of()))
                .toList();
        return Estimate.reconstitute(UUID.randomUUID(), serviceOrder.id(), serviceOrder.openDiagnosisId(),
                serviceOrder.customerId(), CREATED_AT, CREATED_AT.plusSeconds(86_400), lines, EstimateStatus.SENT);
    }

    private static UUID executionId(ServiceOrder serviceOrder, int index) {
        ServiceExecution execution = serviceOrder.serviceExecutions().get(index);
        return execution.id();
    }
}
