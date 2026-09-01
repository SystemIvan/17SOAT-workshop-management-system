package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity - lives inside the {@link ServiceOrder} aggregate boundary. Not accessed
 * or persisted in isolation.
 */
public class ServiceExecution {

    private final UUID id;
    private final UUID diagnosisId;
    private final UUID catalogServiceId;
    private final String name;
    private final Money price;
    private final UUID diagnosedByTechnicianId;
    private final Instant diagnosedAt;
    private final List<StockRequirement> stockRequirements = new ArrayList<>();
    private final List<StockAvailabilitySnapshot> stockAvailability = new ArrayList<>();

    private ServiceExecutionStatus status;
    private UUID authorizedByEstimateId;
    private UUID assignedTechnicianId;
    private boolean stockRequirementsFrozen;
    private UUID stockReservationId;
    private Instant startedAt;
    private Instant completedAt;

    static ServiceExecution start(
            UUID diagnosisId,
            UUID catalogServiceId,
            String name,
            Money price,
            UUID diagnosedByTechnicianId,
            Instant diagnosedAt) {
        return new ServiceExecution(
                UUID.randomUUID(), diagnosisId, catalogServiceId, name, price, diagnosedByTechnicianId, diagnosedAt);
    }

    private ServiceExecution(
            UUID id, UUID diagnosisId, UUID catalogServiceId, String name, Money price,
            UUID diagnosedByTechnicianId, Instant diagnosedAt) {
        this.id = id;
        this.diagnosisId = diagnosisId;
        this.catalogServiceId = catalogServiceId;
        this.name = name;
        this.price = price;
        this.diagnosedByTechnicianId = diagnosedByTechnicianId;
        this.diagnosedAt = diagnosedAt;
        this.status = ServiceExecutionStatus.PENDING;
    }

    /**
     * Rebuilds a ServiceExecution from previously persisted state. Used exclusively by
     * {@link ServiceOrder#reconstitute} via the persistence adapter.
     */
    public static ServiceExecution reconstitute(
            UUID id,
            UUID diagnosisId,
            UUID catalogServiceId,
            String name,
            Money price,
            ServiceExecutionStatus status,
            UUID authorizedByEstimateId,
            UUID assignedTechnicianId,
            UUID diagnosedByTechnicianId,
            Instant diagnosedAt,
            Instant startedAt,
            Instant completedAt,
            boolean stockRequirementsFrozen,
            UUID stockReservationId,
            List<StockRequirement> stockRequirements,
            List<StockAvailabilitySnapshot> stockAvailability) {
        ServiceExecution execution = new ServiceExecution(
                id, diagnosisId, catalogServiceId, name, price, diagnosedByTechnicianId, diagnosedAt);
        execution.status = status;
        execution.authorizedByEstimateId = authorizedByEstimateId;
        execution.assignedTechnicianId = assignedTechnicianId;
        if (completedAt != null && startedAt == null) {
            throw new IllegalArgumentException("ServiceExecution completedAt requires startedAt");
        }
        if (startedAt != null && completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("ServiceExecution completedAt must not be before startedAt");
        }
        execution.startedAt = startedAt;
        execution.completedAt = completedAt;
        execution.stockRequirementsFrozen = stockRequirementsFrozen;
        execution.stockReservationId = stockReservationId;
        execution.stockRequirements.addAll(stockRequirements);
        execution.stockAvailability.addAll(stockAvailability);
        return execution;
    }

    public static ServiceExecution reconstitute(
            UUID id,
            UUID diagnosisId,
            UUID catalogServiceId,
            String name,
            Money price,
            ServiceExecutionStatus status,
            UUID authorizedByEstimateId,
            UUID assignedTechnicianId,
            List<StockRequirement> stockRequirements) {
        return reconstitute(
                id,
                diagnosisId,
                catalogServiceId,
                name,
                price,
                status,
                authorizedByEstimateId,
                assignedTechnicianId,
                null,
                null,
                null,
                null,
                false,
                null,
                stockRequirements,
                List.of());
    }

    void attachStockRequirement(StockRequirement requirement) {
        if (status != ServiceExecutionStatus.PENDING || stockRequirementsFrozen) {
            throw new IllegalStateException(
                    "Cannot attach a stock requirement to a ServiceExecution in status " + status);
        }
        stockRequirements.add(requirement);
    }

    void authorize(UUID estimateId) {
        requireStatus(ServiceExecutionStatus.PENDING);
        this.authorizedByEstimateId = estimateId;
        this.status = stockRequirements.isEmpty()
                ? ServiceExecutionStatus.READY
                : ServiceExecutionStatus.AWAITING_ITEMS;
    }

    void reject() {
        requireStatus(ServiceExecutionStatus.PENDING);
        this.status = ServiceExecutionStatus.REJECTED;
    }

    /**
     * RF19 - confirmar atribuição de um Technician a uma ServiceExecution.
     */
    void confirmTechnicianAssignment(UUID technicianId) {
        if (status == ServiceExecutionStatus.COMPLETED || status == ServiceExecutionStatus.REJECTED) {
            throw new IllegalStateException(
                    "Cannot assign a technician to a ServiceExecution in status " + status);
        }
        this.assignedTechnicianId = technicianId;
    }

    void freezeStockRequirements() {
        stockRequirementsFrozen = true;
    }

    void replaceStockAvailability(List<StockAvailabilitySnapshot> snapshots) {
        if (status != ServiceExecutionStatus.PENDING) {
            throw new IllegalStateException("Stock availability can only be recorded while execution is PENDING");
        }
        List<StockAvailabilitySnapshot> normalized = snapshots == null ? List.of() : List.copyOf(snapshots);
        if (normalized.stream().map(StockAvailabilitySnapshot::stockItemId).distinct().count() != normalized.size()) {
            throw new IllegalArgumentException("Stock availability cannot contain duplicate stock items");
        }
        stockAvailability.clear();
        stockAvailability.addAll(normalized);
    }

    void confirmStockReservation(UUID reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("Stock reservation id must not be null");
        }
        if (stockReservationId != null) {
            if (stockReservationId.equals(reservationId)) {
                return;
            }
            throw new IllegalStateException("A different stock reservation is already associated with this execution");
        }
        requireStatus(ServiceExecutionStatus.AWAITING_ITEMS);
        if (stockRequirements.isEmpty()) {
            throw new IllegalStateException("An execution without stock requirements cannot confirm a reservation");
        }
        for (int i = 0; i < stockRequirements.size(); i++) {
            StockRequirement requirement = stockRequirements.get(i);
            stockRequirements.set(i, requirement.withReserved());
        }
        stockReservationId = reservationId;
        status = ServiceExecutionStatus.READY;
    }

    /**
     * RF20 - iniciar execução de um serviço.
     */
    void start(Instant startedAt) {
        requireStatus(ServiceExecutionStatus.READY);
        if (assignedTechnicianId == null) {
            throw new IllegalStateException("A technician must be assigned before starting a ServiceExecution");
        }
        if (startedAt == null) {
            throw new IllegalArgumentException("ServiceExecution startedAt must not be null");
        }
        this.startedAt = startedAt;
        this.status = ServiceExecutionStatus.IN_PROGRESS;
    }

    /**
     * RF21 - atualizar progresso de uma execução em andamento.
     */
    void updateProgress(String note) {
        requireStatus(ServiceExecutionStatus.IN_PROGRESS);
        // progress notes are not modeled as domain state yet - guard is what matters here.
    }

    /**
     * RF22 - concluir execução de um serviço.
     */
    void complete(Instant completedAt) {
        requireStatus(ServiceExecutionStatus.IN_PROGRESS);
        if (completedAt == null) {
            throw new IllegalArgumentException("ServiceExecution completedAt must not be null");
        }
        if (startedAt == null) {
            throw new IllegalStateException("ServiceExecution must have startedAt before completion");
        }
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalStateException("ServiceExecution completedAt must not be before startedAt");
        }
        this.completedAt = completedAt;
        this.status = ServiceExecutionStatus.COMPLETED;
    }

    private void requireStatus(ServiceExecutionStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Expected ServiceExecution status " + expected + " but was " + status);
        }
    }

    public UUID id() {
        return id;
    }

    public UUID diagnosisId() {
        return diagnosisId;
    }

    public UUID catalogServiceId() {
        return catalogServiceId;
    }

    public String name() {
        return name;
    }

    public Money price() {
        return price;
    }

    public ServiceExecutionStatus status() {
        return status;
    }

    public UUID authorizedByEstimateId() {
        return authorizedByEstimateId;
    }

    public UUID assignedTechnicianId() {
        return assignedTechnicianId;
    }

    public UUID diagnosedByTechnicianId() {
        return diagnosedByTechnicianId;
    }

    public Instant diagnosedAt() {
        return diagnosedAt;
    }

    public boolean stockRequirementsFrozen() {
        return stockRequirementsFrozen;
    }

    public UUID stockReservationId() {
        return stockReservationId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public List<StockRequirement> stockRequirements() {
        return List.copyOf(stockRequirements);
    }

    public List<StockAvailabilitySnapshot> stockAvailability() {
        return List.copyOf(stockAvailability);
    }
}
