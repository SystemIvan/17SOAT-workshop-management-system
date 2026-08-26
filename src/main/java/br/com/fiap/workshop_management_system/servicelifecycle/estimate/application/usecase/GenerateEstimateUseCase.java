package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.event.EstimateGenerated;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockAvailability;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemNotFoundException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilitySnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilityStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentApi;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentExecution;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentResult;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GenerateEstimateUseCase {

    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(48);

    private final ServiceOrderRepository serviceOrderRepository;
    private final EstimateRepository estimateRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final RepairStockAssessmentApi repairStockAssessmentApi;

    @Autowired
    public GenerateEstimateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            ApplicationEventPublisher eventPublisher,
            RepairStockAssessmentApi repairStockAssessmentApi) {
        this(serviceOrderRepository, estimateRepository, Clock.systemUTC(), eventPublisher, repairStockAssessmentApi);
    }

    GenerateEstimateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            Clock clock) {
        this(serviceOrderRepository, estimateRepository, clock, event -> { }, null);
    }

    GenerateEstimateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this(serviceOrderRepository, estimateRepository, clock, eventPublisher, null);
    }

    GenerateEstimateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher,
            RepairStockAssessmentApi repairStockAssessmentApi) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.estimateRepository = estimateRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
        this.repairStockAssessmentApi = repairStockAssessmentApi;
    }

    @Transactional
    public Result execute(UUID serviceOrderId, UUID diagnosisId) {
        ServiceOrder serviceOrder = serviceOrderRepository.findByIdForUpdate(serviceOrderId)
                .orElseThrow(() -> new NoSuchElementException(
                        "ServiceOrder not found: " + serviceOrderId));

        validateDiagnosis(serviceOrder, diagnosisId);

        if (estimateRepository.existsByDiagnosisId(diagnosisId)) {
            throw new IllegalStateException(
                    "Estimate already exists for diagnosis: " + diagnosisId);
        }

        List<ServiceExecution> executions = serviceOrder.serviceExecutions().stream()
                .filter(execution -> diagnosisId.equals(execution.diagnosisId()))
                .toList();

        if (executions.isEmpty()) {
            throw new IllegalStateException(
                    "Diagnosis has no service executions: " + diagnosisId);
        }

        serviceOrder.freezeStockRequirements(diagnosisId);
        if (repairStockAssessmentApi != null) {
            RepairStockAssessmentResult result;
            try {
                result = repairStockAssessmentApi.assessAndRecord(
                        new RepairStockAssessmentCommand(toAssessmentExecutions(executions)));
            } catch (StockItemNotFoundException exception) {
                throw new ServiceOrderStockItemNotFoundException();
            }
            serviceOrder.recordStockAvailability(diagnosisId, toSnapshots(result));
            executions = serviceOrder.serviceExecutions().stream()
                    .filter(execution -> diagnosisId.equals(execution.diagnosisId())).toList();
        }
        List<EstimateLine> lines = executions.stream().map(this::toEstimateLine).toList();

        Instant createdAt = clock.instant();
        Instant expiresAt = createdAt.plus(DEFAULT_EXPIRATION);

        Estimate estimate = Estimate.create(
                serviceOrder.id(),
                diagnosisId,
                serviceOrder.customerId(),
                createdAt,
                expiresAt,
                lines
        );
        estimate.markSent();

        estimateRepository.save(estimate);
        serviceOrderRepository.save(serviceOrder);

        EstimateGenerated event = EstimateGenerated.from(
                estimate.id(),
                estimate.serviceOrderId(),
                estimate.diagnosisId(),
                estimate.customerId(),
                createdAt,
                expiresAt
        );

        eventPublisher.publishEvent(event);

        return new Result(estimate, event);
    }

    private void validateDiagnosis(ServiceOrder serviceOrder, UUID diagnosisId) {
        if (diagnosisId == null) {
            throw new IllegalArgumentException("diagnosisId must not be null");
        }

        if (serviceOrder.openDiagnosisId() == null
                || !serviceOrder.openDiagnosisId().equals(diagnosisId)) {
            throw new IllegalStateException(
                    "Diagnosis is not open for ServiceOrder: " + diagnosisId);
        }
    }

    private EstimateLine toEstimateLine(ServiceExecution execution) {
        List<EstimateStockItem> stockItems = execution.stockRequirements().stream()
                .map(this::toEstimateStockItem)
                .toList();

        return new EstimateLine(
                execution.id(),
                execution.name(),
                execution.price(),
                stockItems,
                execution.stockAvailability().stream().map(snapshot -> new EstimateStockAvailability(
                        snapshot.stockItemId(), snapshot.requestedQuantity(), snapshot.observedAvailableQuantity(),
                        snapshot.shortageQuantity(), snapshot.status(), snapshot.observedAt())).toList()
        );
    }

    private EstimateStockItem toEstimateStockItem(StockRequirement requirement) {
        return new EstimateStockItem(
                requirement.stockItemId(),
                requirement.type(),
                requirement.quantity(),
                requirement.nameSnapshot(),
                requirement.priceSnapshot()
        );
    }

    private List<RepairStockAssessmentExecution> toAssessmentExecutions(List<ServiceExecution> executions) {
        return executions.stream().map(execution -> new RepairStockAssessmentExecution(execution.id(),
                execution.stockRequirements().stream().collect(Collectors.groupingBy(
                        StockRequirement::stockItemId, Collectors.summingInt(StockRequirement::quantity)))
                        .entrySet().stream().map(entry -> new RepairStockAssessmentLine(entry.getKey(), entry.getValue()))
                        .toList())).filter(execution -> !execution.lines().isEmpty()).toList();
    }

    private Map<UUID, List<StockAvailabilitySnapshot>> toSnapshots(RepairStockAssessmentResult result) {
        return result.executions().stream().collect(Collectors.toMap(
                execution -> execution.serviceExecutionId(),
                execution -> execution.lines().stream().map(line -> new StockAvailabilitySnapshot(
                        line.stockItemId(), line.requestedQuantity(), line.observedAvailableQuantity(),
                        line.shortageQuantity(), StockAvailabilityStatus.valueOf(line.status().name()), line.observedAt()))
                        .toList()));
    }

    public record Result(
            Estimate estimate,
            EstimateGenerated event
    ) {
    }
}
