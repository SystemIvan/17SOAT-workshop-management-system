package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate Root. The only entry point for changing anything inside its boundary
 * (including {@link ServiceExecution}).
 */
public class ServiceOrder {

    private final UUID id;
    private final UUID customerId;
    private final UUID vehicleId;
    private final VehicleSnapshot vehicleSnapshot;
    private final Set<UUID> approvedEstimateIds = new LinkedHashSet<>();
    private final List<ServiceExecution> serviceExecutions = new ArrayList<>();

    private Priority priority;
    private ServiceOrderStatus statusSnapshot;
    private UUID openDiagnosisId;

    /**
     * Integration points for the Diagnoses & Estimate bounded context (Epic 2):
     * ServiceOrder does not hold Estimate objects (one-way reference), so it relies
     * on these flags - toggled by policies reacting to Estimate domain events - to
     * know what to show in the derived status.
     */
    private boolean hasSentEstimateWithPendingLines;

    public static ServiceOrder create(UUID customerId, UUID vehicleId, VehicleSnapshot vehicleSnapshot) {
        return create(customerId, vehicleId, vehicleSnapshot, Priority.NORMAL);
    }

    public static ServiceOrder create(UUID customerId, UUID vehicleId, VehicleSnapshot vehicleSnapshot, Priority priority) {
        ServiceOrder serviceOrder = new ServiceOrder(UUID.randomUUID(), customerId, vehicleId, vehicleSnapshot, priority);
        serviceOrder.statusSnapshot = ServiceOrderStatus.RECEIVED;
        return serviceOrder;
    }

    private ServiceOrder(UUID id, UUID customerId, UUID vehicleId, VehicleSnapshot vehicleSnapshot, Priority priority) {
        this.id = id;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.vehicleSnapshot = vehicleSnapshot;
        this.priority = priority;
    }

    /**
     * Rebuilds a ServiceOrder from previously persisted state. Used exclusively by the
     * persistence adapter - unlike {@link #create}, it does not run creation rules and
     * restores the exact state given (id, snapshots, executions, flags).
     */
    public static ServiceOrder reconstitute(
            UUID id,
            UUID customerId,
            UUID vehicleId,
            VehicleSnapshot vehicleSnapshot,
            Priority priority,
            ServiceOrderStatus statusSnapshot,
            UUID openDiagnosisId,
            boolean hasSentEstimateWithPendingLines,
            Set<UUID> approvedEstimateIds,
            List<ServiceExecution> serviceExecutions) {
        ServiceOrder serviceOrder = new ServiceOrder(id, customerId, vehicleId, vehicleSnapshot, priority);
        serviceOrder.statusSnapshot = statusSnapshot;
        serviceOrder.openDiagnosisId = openDiagnosisId;
        serviceOrder.hasSentEstimateWithPendingLines = hasSentEstimateWithPendingLines;
        serviceOrder.approvedEstimateIds.addAll(approvedEstimateIds);
        serviceOrder.serviceExecutions.addAll(serviceExecutions);
        return serviceOrder;
    }

    /**
     * RF10 - alterar a prioridade de uma Service Order já existente.
     */
    public void definePriority(Priority newPriority) {
        if (statusSnapshot == ServiceOrderStatus.COMPLETED || statusSnapshot == ServiceOrderStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Priority cannot be changed when the ServiceOrder is COMPLETED or DELIVERED");
        }
        this.priority = newPriority;
    }

    public UUID performDiagnosis(List<DiagnosisItem> items) {
        if (openDiagnosisId != null) {
            throw new IllegalStateException("A diagnosis is already open without an Estimate generated for it");
        }
        UUID diagnosisId = UUID.randomUUID();
        for (DiagnosisItem item : items) {
            ServiceExecution execution = ServiceExecution.start(diagnosisId, item.catalogServiceId(), item.name(), item.price());
            item.stockRequirements().forEach(execution::attachStockRequirement);
            serviceExecutions.add(execution);
        }
        this.openDiagnosisId = diagnosisId;
        recomputeStatusSnapshot(false);
        return diagnosisId;
    }

    public UUID addServiceExecution(UUID diagnosisId, UUID catalogServiceId, String name, Money price) {
        if (openDiagnosisId == null || !openDiagnosisId.equals(diagnosisId)) {
            throw new IllegalStateException("Diagnosis " + diagnosisId + " is not open for new service executions");
        }
        ServiceExecution execution = ServiceExecution.start(diagnosisId, catalogServiceId, name, price);
        serviceExecutions.add(execution);
        recomputeStatusSnapshot(false);
        return execution.id();
    }

    public void attachStockRequirement(UUID serviceExecutionId, StockRequirement requirement) {
        findExecution(serviceExecutionId).attachStockRequirement(requirement);
    }

    public void freezeStockRequirements(UUID diagnosisId) {
        serviceExecutions.stream()
                .filter(execution -> execution.diagnosisId().equals(diagnosisId))
                .forEach(ServiceExecution::freezeStockRequirements);
    }

    /**
     * Triggered by the policy "EstimateLineService decision == approved" (Epic 2).
     * Also marks the owning diagnosis as no longer open, since an Estimate now covers it.
     */
    public void authorizeExecutionFromEstimate(UUID estimateId, UUID serviceExecutionId) {
        ServiceExecution execution = findExecution(serviceExecutionId);
        execution.authorize(estimateId);
        approvedEstimateIds.add(estimateId);
        clearOpenDiagnosisIfCoveredBy(execution.diagnosisId());
        recomputeStatusSnapshot(false);
    }

    /**
     * Triggered by the policy "EstimateLineService decision == rejected" (Epic 2).
     */
    public void rejectExecutionFromEstimate(UUID estimateId, UUID serviceExecutionId) {
        ServiceExecution execution = findExecution(serviceExecutionId);
        execution.reject();
        clearOpenDiagnosisIfCoveredBy(execution.diagnosisId());
        recomputeStatusSnapshot(false);
    }

    public void confirmStockReservation(UUID serviceExecutionId, UUID stockReservationId) {
        findExecution(serviceExecutionId).confirmStockReservation(stockReservationId);
        recomputeStatusSnapshot(false);
    }

    /**
     * RF19 - confirmar atribuição de um Technician a uma ServiceExecution.
     */
    public void confirmTechnicianAssignment(UUID serviceExecutionId, UUID technicianId) {
        findExecution(serviceExecutionId).confirmTechnicianAssignment(technicianId);
    }

    /**
     * RF20 - iniciar execução de um serviço.
     */
    public void startExecution(UUID serviceExecutionId) {
        findExecution(serviceExecutionId).start();
        recomputeStatusSnapshot(false);
    }

    /**
     * RF21 - atualizar progresso de uma execução em andamento.
     */
    public void updateExecutionProgress(UUID serviceExecutionId, String note) {
        findExecution(serviceExecutionId).updateProgress(note);
    }

    /**
     * RF22 - concluir execução de um serviço.
     */
    public void completeExecution(UUID serviceExecutionId) {
        findExecution(serviceExecutionId).complete();
        recomputeStatusSnapshot(false);
    }

    /**
     * RF24 - finalizar a Service Order (entrega do veículo ao Customer).
     */
    public void finalize(boolean vehicleDelivered) {
        if (statusSnapshot != ServiceOrderStatus.COMPLETED || !vehicleDelivered) {
            throw new IllegalStateException(
                    "ServiceOrder can only be finalized when status is COMPLETED and the vehicle was delivered");
        }
        recomputeStatusSnapshot(true);
    }

    /**
     * Integration points for the Diagnoses & Estimate bounded context (Epic 2).
     */
    public void markEstimateSentWithPendingLines() {
        this.hasSentEstimateWithPendingLines = true;
        recomputeStatusSnapshot(false);
    }

    public void markEstimateFullyDecided() {
        this.hasSentEstimateWithPendingLines = false;
        recomputeStatusSnapshot(false);
    }

    /**
     * RF23 - consultar o status derivado (tracking) da Service Order.
     * Read-only: just returns the stored value, no computation happens here.
     */
    public ServiceOrderStatus status() {
        return statusSnapshot;
    }

    private void clearOpenDiagnosisIfCoveredBy(UUID diagnosisId) {
        if (diagnosisId.equals(openDiagnosisId)) {
            boolean anyStillPending = serviceExecutions.stream()
                    .filter(execution -> execution.diagnosisId().equals(diagnosisId))
                    .anyMatch(execution -> execution.status() == ServiceExecutionStatus.PENDING);
            if (!anyStillPending) {
                this.openDiagnosisId = null;
            }
        }
    }

    private ServiceExecution findExecution(UUID serviceExecutionId) {
        return serviceExecutions.stream()
                .filter(execution -> execution.id().equals(serviceExecutionId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("ServiceExecution not found: " + serviceExecutionId));
    }

    /**
     * Evaluated by precedence - the first branch that matches wins. Called at the end
     * of every command above that can affect the derived status; the result is stored
     * and just read back by {@link #status()} on queries.
     */
    private void recomputeStatusSnapshot(boolean vehicleDelivered) {
        if (vehicleDelivered) {
            statusSnapshot = ServiceOrderStatus.DELIVERED;
        } else if (allNonRejectedExecutionsCompleted()) {
            statusSnapshot = ServiceOrderStatus.COMPLETED;
        } else if (anyExecutionInStatus(ServiceExecutionStatus.IN_PROGRESS)) {
            statusSnapshot = ServiceOrderStatus.IN_PROGRESS;
        } else if (anyExecutionInStatus(ServiceExecutionStatus.AWAITING_ITEMS)) {
            statusSnapshot = ServiceOrderStatus.AWAITING_ITEMS;
        } else if (hasSentEstimateWithPendingLines) {
            statusSnapshot = ServiceOrderStatus.AWAITING_APPROVAL;
        } else if (openDiagnosisId != null) {
            statusSnapshot = ServiceOrderStatus.IN_DIAGNOSIS;
        } else {
            statusSnapshot = ServiceOrderStatus.RECEIVED;
        }
    }

    private boolean allNonRejectedExecutionsCompleted() {
        List<ServiceExecution> nonRejected = serviceExecutions.stream()
                .filter(execution -> execution.status() != ServiceExecutionStatus.REJECTED)
                .toList();
        return !nonRejected.isEmpty()
                && nonRejected.stream().allMatch(execution -> execution.status() == ServiceExecutionStatus.COMPLETED);
    }

    private boolean anyExecutionInStatus(ServiceExecutionStatus status) {
        return serviceExecutions.stream().anyMatch(execution -> execution.status() == status);
    }

    public UUID id() {
        return id;
    }

    public UUID customerId() {
        return customerId;
    }

    public UUID vehicleId() {
        return vehicleId;
    }

    public VehicleSnapshot vehicleSnapshot() {
        return vehicleSnapshot;
    }

    public Priority priority() {
        return priority;
    }

    public Set<UUID> approvedEstimateIds() {
        return Set.copyOf(approvedEstimateIds);
    }

    public List<ServiceExecution> serviceExecutions() {
        return List.copyOf(serviceExecutions);
    }

    public UUID openDiagnosisId() {
        return openDiagnosisId;
    }

    public boolean hasSentEstimateWithPendingLines() {
        return hasSentEstimateWithPendingLines;
    }
}
