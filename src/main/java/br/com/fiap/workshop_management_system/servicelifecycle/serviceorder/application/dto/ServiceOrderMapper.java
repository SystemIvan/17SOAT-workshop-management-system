package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.DiagnosisItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockRequirement;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.VehicleSnapshot;

import java.util.List;

/**
 * Converts between the ServiceOrder aggregate and the application-layer DTOs.
 * Entities never cross the controller boundary directly.
 */
public final class ServiceOrderMapper {

    private ServiceOrderMapper() {
    }

    public static ServiceOrderResponse toResponse(ServiceOrder serviceOrder) {
        var status = serviceOrder.status();
        return new ServiceOrderResponse(
                serviceOrder.id(),
                serviceOrder.customerId(),
                serviceOrder.vehicleId(),
                toVehicleSnapshotResponse(serviceOrder.vehicleSnapshot()),
                serviceOrder.priority(),
                serviceOrder.initialAssessment(),
                serviceOrder.diagnosisAssigneeId(),
                status,
                status,
                serviceOrder.approvedEstimateIds(),
                serviceOrder.serviceExecutions().stream().map(ServiceOrderMapper::toExecutionResponse).toList());
    }

    public static ServiceOrderStatusResponse toStatusResponse(ServiceOrder serviceOrder) {
        return new ServiceOrderStatusResponse(serviceOrder.id(), serviceOrder.status());
    }

    public static ServiceExecutionResponse toExecutionResponse(ServiceExecution execution) {
        return new ServiceExecutionResponse(
                execution.id(),
                execution.diagnosisId(),
                execution.catalogServiceId(),
                execution.name(),
                toMoneyDTO(execution.price()),
                execution.status(),
                execution.authorizedByEstimateId(),
                execution.assignedTechnicianId(),
                execution.diagnosedByTechnicianId(),
                execution.diagnosedAt(),
                execution.stockReservationId(),
                execution.stockRequirements().stream().map(ServiceOrderMapper::toStockRequirementResponse).toList());
    }

    public static VehicleSnapshot toVehicleSnapshot(VehicleSnapshotRequest request) {
        return new VehicleSnapshot(request.licensePlate(), request.brand(), request.model(), request.year());
    }

    public static Money toMoney(MoneyDTO dto) {
        return new Money(dto.value(), dto.currency());
    }

    public static MoneyDTO toMoneyDTO(Money money) {
        return new MoneyDTO(money.value(), money.currency());
    }

    public static List<DiagnosisItem> toDiagnosisItems(List<DiagnosisItemRequest> requests) {
        return requests.stream().map(ServiceOrderMapper::toDiagnosisItem).toList();
    }

    public static DiagnosisItem toDiagnosisItem(DiagnosisItemRequest request) {
        List<StockRequirement> stockRequirements = request.stockRequirements() == null
                ? List.of()
                : request.stockRequirements().stream().map(ServiceOrderMapper::toStockRequirement).toList();
        return new DiagnosisItem(request.catalogServiceId(), request.name(), toMoney(request.price()), stockRequirements);
    }

    public static StockRequirement toStockRequirement(StockRequirementRequest request) {
        return new StockRequirement(
                request.stockItemId(),
                request.type(),
                request.quantity(),
                request.nameSnapshot(),
                toMoney(request.priceSnapshot()),
                false);
    }

    public static StockRequirementResponse toStockRequirementResponse(StockRequirement stockRequirement) {
        return new StockRequirementResponse(
                stockRequirement.stockItemId(),
                stockRequirement.type(),
                stockRequirement.quantity(),
                stockRequirement.nameSnapshot(),
                toMoneyDTO(stockRequirement.priceSnapshot()),
                stockRequirement.reserved());
    }

    public static VehicleSnapshotResponse toVehicleSnapshotResponse(VehicleSnapshot vehicleSnapshot) {
        return new VehicleSnapshotResponse(
                vehicleSnapshot.licensePlate(),
                vehicleSnapshot.brand(),
                vehicleSnapshot.model(),
                vehicleSnapshot.year());
    }
}
