package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.usecase;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.PerformDiagnosisRequest;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderMapper;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto.ServiceOrderResponse;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceArchivedForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .CatalogServiceNotFoundForNewWorkException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderStockItemNotFoundException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CatalogServiceEligibility;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port
        .CatalogServiceEligibilityPort;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilitySnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilityStatus;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.repository.TechnicianRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentApi;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentCommand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentExecution;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentLine;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api.RepairStockAssessmentResult;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PerformDiagnosisUseCase {

    private final ServiceOrderRepository repository;
    private final TechnicianRepository technicianRepository;
    private final CatalogServiceEligibilityPort catalogServiceEligibilityPort;
    private final RepairStockAssessmentApi repairStockAssessmentApi;
    private final Clock clock;

    @Autowired
    public PerformDiagnosisUseCase(
            ServiceOrderRepository repository,
            TechnicianRepository technicianRepository,
            CatalogServiceEligibilityPort catalogServiceEligibilityPort,
            RepairStockAssessmentApi repairStockAssessmentApi) {
        this(repository, technicianRepository, catalogServiceEligibilityPort, repairStockAssessmentApi, Clock.systemUTC());
    }

    PerformDiagnosisUseCase(
            ServiceOrderRepository repository,
            TechnicianRepository technicianRepository,
            CatalogServiceEligibilityPort catalogServiceEligibilityPort) {
        this(repository, technicianRepository, catalogServiceEligibilityPort, null, Clock.systemUTC());
    }

    PerformDiagnosisUseCase(
            ServiceOrderRepository repository,
            TechnicianRepository technicianRepository,
            CatalogServiceEligibilityPort catalogServiceEligibilityPort,
            Clock clock) {
        this(repository, technicianRepository, catalogServiceEligibilityPort, null, clock);
    }

    PerformDiagnosisUseCase(
            ServiceOrderRepository repository,
            TechnicianRepository technicianRepository,
            CatalogServiceEligibilityPort catalogServiceEligibilityPort,
            RepairStockAssessmentApi repairStockAssessmentApi,
            Clock clock) {
        this.repository = repository;
        this.technicianRepository = technicianRepository;
        this.catalogServiceEligibilityPort = catalogServiceEligibilityPort;
        this.repairStockAssessmentApi = repairStockAssessmentApi;
        this.clock = clock;
    }

    @Transactional
    public ServiceOrderResponse execute(UUID serviceOrderId, PerformDiagnosisRequest request) {
        List<DiagnosisItem> items = ServiceOrderMapper.toDiagnosisItems(request.items());
        technicianRepository.findById(request.diagnosedByTechnicianId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Technician not found: " + request.diagnosedByTechnicianId()));
        ServiceOrder serviceOrder = ServiceOrderFinder.getOrThrowForUpdate(repository, serviceOrderId);
        ensureCatalogServicesEligible(request);
        Instant diagnosedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID diagnosisId = serviceOrder.performDiagnosis(items, request.diagnosedByTechnicianId(), diagnosedAt);
        if (repairStockAssessmentApi != null) {
            RepairStockAssessmentResult result;
            try {
                result = repairStockAssessmentApi.assessAndRecord(
                        new RepairStockAssessmentCommand(toAssessmentExecutions(serviceOrder, diagnosisId)));
            } catch (StockItemNotFoundException exception) {
                throw new ServiceOrderStockItemNotFoundException();
            }
            serviceOrder.recordStockAvailability(diagnosisId, toSnapshots(result));
        }
        repository.save(serviceOrder);
        return ServiceOrderMapper.toResponse(serviceOrder);
    }

    private List<RepairStockAssessmentExecution> toAssessmentExecutions(ServiceOrder serviceOrder, UUID diagnosisId) {
        return serviceOrder.serviceExecutions().stream()
                .filter(execution -> diagnosisId.equals(execution.diagnosisId()))
                .map(execution -> new RepairStockAssessmentExecution(execution.id(), execution.stockRequirements().stream()
                        .collect(Collectors.groupingBy(requirement -> requirement.stockItemId(), Collectors.summingInt(
                                requirement -> requirement.quantity())))
                        .entrySet().stream().map(entry -> new RepairStockAssessmentLine(entry.getKey(), entry.getValue()))
                        .toList()))
                .filter(execution -> !execution.lines().isEmpty())
                .toList();
    }

    private Map<UUID, List<StockAvailabilitySnapshot>> toSnapshots(RepairStockAssessmentResult result) {
        return result.executions().stream().collect(Collectors.toMap(
                execution -> execution.serviceExecutionId(),
                execution -> execution.lines().stream().map(line -> new StockAvailabilitySnapshot(
                        line.stockItemId(), line.requestedQuantity(), line.observedAvailableQuantity(),
                        line.shortageQuantity(), StockAvailabilityStatus.valueOf(line.status().name()), line.observedAt()))
                        .toList()));
    }

    private void ensureCatalogServicesEligible(PerformDiagnosisRequest request) {
        request.items().stream()
                .map(item -> item.catalogServiceId())
                .distinct()
                .sorted()
                .forEach(this::ensureCatalogServiceEligible);
    }

    private void ensureCatalogServiceEligible(UUID catalogServiceId) {
        CatalogServiceEligibility eligibility = catalogServiceEligibilityPort.checkForNewWork(catalogServiceId);
        switch (eligibility) {
            case ACTIVE -> {
            }
            case ARCHIVED -> throw new CatalogServiceArchivedForNewWorkException();
            case NOT_FOUND -> throw new CatalogServiceNotFoundForNewWorkException();
        }
    }
}
