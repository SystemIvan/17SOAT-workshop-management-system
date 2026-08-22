package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

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
    private final List<StockRequirement> stockRequirements = new ArrayList<>();

    private ServiceExecutionStatus status;
    private UUID authorizedByEstimateId;
    private UUID assignedTechnicianId;
    private boolean stockRequirementsFrozen;
    private UUID stockReservationId;

    static ServiceExecution start(UUID diagnosisId, UUID catalogServiceId, String name, Money price) {
        return new ServiceExecution(UUID.randomUUID(), diagnosisId, catalogServiceId, name, price);
    }

    private ServiceExecution(UUID id, UUID diagnosisId, UUID catalogServiceId, String name, Money price) {
        this.id = id;
        this.diagnosisId = diagnosisId;
        this.catalogServiceId = catalogServiceId;
        this.name = name;
        this.price = price;
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
            boolean stockRequirementsFrozen,
            UUID stockReservationId,
            List<StockRequirement> stockRequirements) {
        ServiceExecution execution = new ServiceExecution(id, diagnosisId, catalogServiceId, name, price);
        execution.status = status;
        execution.authorizedByEstimateId = authorizedByEstimateId;
        execution.assignedTechnicianId = assignedTechnicianId;
        execution.stockRequirementsFrozen = stockRequirementsFrozen;
        execution.stockReservationId = stockReservationId;
        execution.stockRequirements.addAll(stockRequirements);
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
                false,
                null,
                stockRequirements);
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
    void start() {
        requireStatus(ServiceExecutionStatus.READY);
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
    void complete() {
        requireStatus(ServiceExecutionStatus.IN_PROGRESS);
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

    public boolean stockRequirementsFrozen() {
        return stockRequirementsFrozen;
    }

    public UUID stockReservationId() {
        return stockReservationId;
    }

    public List<StockRequirement> stockRequirements() {
        return List.copyOf(stockRequirements);
    }
}
