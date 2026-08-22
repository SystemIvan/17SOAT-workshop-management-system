package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.event.EstimateGenerated;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.repository.EstimateRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class GenerateEstimateUseCase {

    private static final Duration DEFAULT_EXPIRATION = Duration.ofHours(48);

    private final ServiceOrderRepository serviceOrderRepository;
    private final EstimateRepository estimateRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public GenerateEstimateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            ApplicationEventPublisher eventPublisher) {
        this(serviceOrderRepository, estimateRepository, Clock.systemUTC(), eventPublisher);
    }

    GenerateEstimateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            Clock clock) {
        this(serviceOrderRepository, estimateRepository, clock, event -> { });
    }

    GenerateEstimateUseCase(
            ServiceOrderRepository serviceOrderRepository,
            EstimateRepository estimateRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher) {
        this.serviceOrderRepository = serviceOrderRepository;
        this.estimateRepository = estimateRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
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

        List<EstimateLine> lines = executions.stream()
                .map(this::toEstimateLine)
                .toList();

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

        serviceOrder.freezeStockRequirements(diagnosisId);
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
                stockItems
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

    public record Result(
            Estimate estimate,
            EstimateGenerated event
    ) {
    }
}
