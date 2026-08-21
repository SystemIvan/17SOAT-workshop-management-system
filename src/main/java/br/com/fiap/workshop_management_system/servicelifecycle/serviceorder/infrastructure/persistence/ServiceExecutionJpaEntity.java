package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecutionStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA projection of {@link br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceExecution}.
 * Persisted only as part of the {@link ServiceOrderJpaEntity} aggregate.
 */
@Entity
@Table(name = "service_executions")
public class ServiceExecutionJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_order_id", nullable = false)
    private ServiceOrderJpaEntity serviceOrder;

    private UUID diagnosisId;
    private UUID catalogServiceId;
    private String name;

    @Column(name = "price_value")
    private BigDecimal priceValue;

    @Column(name = "price_currency")
    private String priceCurrency;

    @Enumerated(EnumType.STRING)
    private ServiceExecutionStatus status;

    private UUID authorizedByEstimateId;
    private UUID assignedTechnicianId;
    private boolean stockRequirementsFrozen;
    private UUID stockReservationId;

    @ElementCollection
    @CollectionTable(name = "service_execution_stock_requirements", joinColumns = @JoinColumn(name = "service_execution_id"))
    private List<StockRequirementEmbeddable> stockRequirements = new ArrayList<>();

    protected ServiceExecutionJpaEntity() {
    }

    public ServiceExecutionJpaEntity(
            UUID id,
            ServiceOrderJpaEntity serviceOrder,
            UUID diagnosisId,
            UUID catalogServiceId,
            String name,
            BigDecimal priceValue,
            String priceCurrency,
            ServiceExecutionStatus status,
            UUID authorizedByEstimateId,
            UUID assignedTechnicianId,
            boolean stockRequirementsFrozen,
            UUID stockReservationId,
            List<StockRequirementEmbeddable> stockRequirements) {
        this.id = id;
        this.serviceOrder = serviceOrder;
        this.diagnosisId = diagnosisId;
        this.catalogServiceId = catalogServiceId;
        this.name = name;
        this.priceValue = priceValue;
        this.priceCurrency = priceCurrency;
        this.status = status;
        this.authorizedByEstimateId = authorizedByEstimateId;
        this.assignedTechnicianId = assignedTechnicianId;
        this.stockRequirementsFrozen = stockRequirementsFrozen;
        this.stockReservationId = stockReservationId;
        this.stockRequirements = stockRequirements;
    }

    public UUID getId() {
        return id;
    }

    public ServiceOrderJpaEntity getServiceOrder() {
        return serviceOrder;
    }

    public UUID getDiagnosisId() {
        return diagnosisId;
    }

    public UUID getCatalogServiceId() {
        return catalogServiceId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPriceValue() {
        return priceValue;
    }

    public String getPriceCurrency() {
        return priceCurrency;
    }

    public ServiceExecutionStatus getStatus() {
        return status;
    }

    public UUID getAuthorizedByEstimateId() {
        return authorizedByEstimateId;
    }

    public UUID getAssignedTechnicianId() {
        return assignedTechnicianId;
    }

    public boolean isStockRequirementsFrozen() {
        return stockRequirementsFrozen;
    }

    public UUID getStockReservationId() {
        return stockReservationId;
    }

    public List<StockRequirementEmbeddable> getStockRequirements() {
        return stockRequirements;
    }
}
