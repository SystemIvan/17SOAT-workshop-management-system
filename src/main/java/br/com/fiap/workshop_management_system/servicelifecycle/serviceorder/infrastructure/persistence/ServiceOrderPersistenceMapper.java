package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilitySnapshot;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts between the framework-agnostic {@link ServiceOrder} aggregate and its JPA
 * projection. Reconstruction of the domain object goes through
 * {@link ServiceOrder#reconstitute} / {@link ServiceExecution#reconstitute}, which restore
 * exact persisted state without re-running creation rules.
 */
@Component
public class ServiceOrderPersistenceMapper {

    public ServiceOrderJpaEntity toEntity(ServiceOrder serviceOrder) {
        VehicleSnapshot vehicleSnapshot = serviceOrder.vehicleSnapshot();
        ServiceOrderJpaEntity entity = new ServiceOrderJpaEntity(
                serviceOrder.id(),
                serviceOrder.customerId(),
                serviceOrder.vehicleId(),
                vehicleSnapshot.licensePlate(),
                vehicleSnapshot.brand(),
                vehicleSnapshot.model(),
                vehicleSnapshot.year(),
                serviceOrder.initialAssessment(),
                serviceOrder.diagnosisAssigneeId(),
                serviceOrder.priority(),
                serviceOrder.status(),
                serviceOrder.openDiagnosisId(),
                serviceOrder.hasSentEstimateWithPendingLines(),
                serviceOrder.approvedEstimateIds());
        for (ServiceExecution execution : serviceOrder.serviceExecutions()) {
            entity.addExecution(toExecutionEntity(execution, entity));
        }
        return entity;
    }

    public ServiceOrder toDomain(ServiceOrderJpaEntity entity) {
        VehicleSnapshot vehicleSnapshot = new VehicleSnapshot(
                entity.getVehicleLicensePlate(),
                entity.getVehicleBrand(),
                entity.getVehicleModel(),
                entity.getVehicleYear());
        List<ServiceExecution> executions = entity.getExecutions().stream()
                .map(this::toExecutionDomain)
                .toList();
        return ServiceOrder.reconstitute(
                entity.getId(),
                entity.getCustomerId(),
                entity.getVehicleId(),
                vehicleSnapshot,
                entity.getInitialAssessment(),
                entity.getDiagnosisAssigneeId(),
                entity.getPriority(),
                entity.getStatusSnapshot(),
                entity.getOpenDiagnosisId(),
                entity.isHasSentEstimateWithPendingLines(),
                entity.getApprovedEstimateIds(),
                executions);
    }

    private ServiceExecutionJpaEntity toExecutionEntity(ServiceExecution execution, ServiceOrderJpaEntity owner) {
        List<StockRequirementEmbeddable> stockRequirements = execution.stockRequirements().stream()
                .map(this::toStockRequirementEmbeddable)
                .toList();
        List<StockAvailabilitySnapshotEmbeddable> stockAvailability = execution.stockAvailability().stream()
                .map(this::toStockAvailabilityEmbeddable).toList();
        return new ServiceExecutionJpaEntity(
                execution.id(),
                owner,
                execution.diagnosisId(),
                execution.catalogServiceId(),
                execution.name(),
                execution.price().value(),
                execution.price().currency(),
                execution.status(),
                execution.authorizedByEstimateId(),
                execution.assignedTechnicianId(),
                execution.diagnosedByTechnicianId(),
                execution.diagnosedAt(),
                execution.startedAt(),
                execution.completedAt(),
                execution.stockRequirementsFrozen(),
                execution.stockReservationId(),
                stockRequirements,
                stockAvailability);
    }

    private ServiceExecution toExecutionDomain(ServiceExecutionJpaEntity entity) {
        List<StockRequirement> stockRequirements = entity.getStockRequirements().stream()
                .map(this::toStockRequirementDomain)
                .toList();
        List<StockAvailabilitySnapshot> stockAvailability = entity.getStockAvailability().stream()
                .map(this::toStockAvailabilitySnapshot).toList();
        return ServiceExecution.reconstitute(
                entity.getId(),
                entity.getDiagnosisId(),
                entity.getCatalogServiceId(),
                entity.getName(),
                new Money(entity.getPriceValue(), entity.getPriceCurrency()),
                entity.getStatus(),
                entity.getAuthorizedByEstimateId(),
                entity.getAssignedTechnicianId(),
                entity.getDiagnosedByTechnicianId(),
                entity.getDiagnosedAt(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.isStockRequirementsFrozen(),
                entity.getStockReservationId(),
                stockRequirements,
                stockAvailability);
    }

    private StockRequirementEmbeddable toStockRequirementEmbeddable(StockRequirement stockRequirement) {
        return new StockRequirementEmbeddable(
                stockRequirement.stockItemId(),
                stockRequirement.type(),
                stockRequirement.quantity(),
                stockRequirement.nameSnapshot(),
                stockRequirement.priceSnapshot().value(),
                stockRequirement.priceSnapshot().currency(),
                stockRequirement.reserved());
    }

    private StockRequirement toStockRequirementDomain(StockRequirementEmbeddable embeddable) {
        return new StockRequirement(
                embeddable.getStockItemId(),
                embeddable.getType(),
                embeddable.getQuantity(),
                embeddable.getNameSnapshot(),
                new Money(embeddable.getPriceSnapshotValue(), embeddable.getPriceSnapshotCurrency()),
                embeddable.isReserved());
    }

    private StockAvailabilitySnapshotEmbeddable toStockAvailabilityEmbeddable(StockAvailabilitySnapshot snapshot) {
        return new StockAvailabilitySnapshotEmbeddable(snapshot.stockItemId(), snapshot.requestedQuantity(),
                snapshot.observedAvailableQuantity(), snapshot.shortageQuantity(), snapshot.status(), snapshot.observedAt());
    }

    private StockAvailabilitySnapshot toStockAvailabilitySnapshot(StockAvailabilitySnapshotEmbeddable embeddable) {
        return new StockAvailabilitySnapshot(embeddable.getStockItemId(), embeddable.getRequestedQuantity(),
                embeddable.getObservedAvailableQuantity(), embeddable.getShortageQuantity(), embeddable.getStatus(),
                embeddable.getObservedAt());
    }
}
