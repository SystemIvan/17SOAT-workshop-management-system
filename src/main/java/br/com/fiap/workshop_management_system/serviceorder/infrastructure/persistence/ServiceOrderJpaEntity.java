package br.com.fiap.workshop_management_system.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.serviceorder.domain.model.Priority;
import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * JPA projection of the {@link br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrder}
 * aggregate. Kept separate from the domain class so the domain stays framework-agnostic.
 *
 * <p>Implements {@link Persistable} because the id (UUID) is always assigned by the domain
 * before persistence - without this, Spring Data would assume every save() is an update
 * (since the id is never null) and skip the insert for brand-new aggregates.
 */
@Entity
@Table(name = "service_orders")
public class ServiceOrderJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    private UUID customerId;
    private UUID vehicleId;

    @Column(name = "vehicle_license_plate")
    private String vehicleLicensePlate;

    @Column(name = "vehicle_brand")
    private String vehicleBrand;

    @Column(name = "vehicle_model")
    private String vehicleModel;

    @Column(name = "vehicle_year")
    private int vehicleYear;

    @Enumerated(EnumType.STRING)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    private ServiceOrderStatus statusSnapshot;

    private UUID openDiagnosisId;

    private boolean hasSentEstimateWithPendingLines;

    @ElementCollection
    @CollectionTable(name = "service_order_approved_estimates", joinColumns = @JoinColumn(name = "service_order_id"))
    @Column(name = "estimate_id")
    private Set<UUID> approvedEstimateIds = new LinkedHashSet<>();

    @OneToMany(mappedBy = "serviceOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceExecutionJpaEntity> executions = new ArrayList<>();

    protected ServiceOrderJpaEntity() {
    }

    public ServiceOrderJpaEntity(
            UUID id,
            UUID customerId,
            UUID vehicleId,
            String vehicleLicensePlate,
            String vehicleBrand,
            String vehicleModel,
            int vehicleYear,
            Priority priority,
            ServiceOrderStatus statusSnapshot,
            UUID openDiagnosisId,
            boolean hasSentEstimateWithPendingLines,
            Set<UUID> approvedEstimateIds) {
        this.id = id;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.vehicleLicensePlate = vehicleLicensePlate;
        this.vehicleBrand = vehicleBrand;
        this.vehicleModel = vehicleModel;
        this.vehicleYear = vehicleYear;
        this.priority = priority;
        this.statusSnapshot = statusSnapshot;
        this.openDiagnosisId = openDiagnosisId;
        this.hasSentEstimateWithPendingLines = hasSentEstimateWithPendingLines;
        this.approvedEstimateIds = approvedEstimateIds;
    }

    public void addExecution(ServiceExecutionJpaEntity execution) {
        executions.add(execution);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public String getVehicleLicensePlate() {
        return vehicleLicensePlate;
    }

    public String getVehicleBrand() {
        return vehicleBrand;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public int getVehicleYear() {
        return vehicleYear;
    }

    public Priority getPriority() {
        return priority;
    }

    public ServiceOrderStatus getStatusSnapshot() {
        return statusSnapshot;
    }

    public UUID getOpenDiagnosisId() {
        return openDiagnosisId;
    }

    public boolean isHasSentEstimateWithPendingLines() {
        return hasSentEstimateWithPendingLines;
    }

    public Set<UUID> getApprovedEstimateIds() {
        return approvedEstimateIds;
    }

    public List<ServiceExecutionJpaEntity> getExecutions() {
        return executions;
    }
}
