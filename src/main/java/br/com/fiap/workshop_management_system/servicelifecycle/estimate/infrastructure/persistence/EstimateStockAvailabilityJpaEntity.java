package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "estimate_line_stock_availability")
@IdClass(EstimateStockAvailabilityJpaEntityId.class)
public class EstimateStockAvailabilityJpaEntity {

    @Id
    @Column(name = "estimate_line_id")
    private UUID estimateLineId;

    @Id
    @Column(name = "stock_item_id")
    private UUID stockItemId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estimate_line_id", nullable = false, insertable = false, updatable = false)
    private EstimateLineJpaEntity estimateLine;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;
    @Column(name = "observed_available_quantity", nullable = false)
    private int observedAvailableQuantity;
    @Column(name = "shortage_quantity", nullable = false)
    private int shortageQuantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockAvailabilityStatus status;
    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected EstimateStockAvailabilityJpaEntity() { }

    public EstimateStockAvailabilityJpaEntity(UUID stockItemId, int requestedQuantity, int observedAvailableQuantity,
            int shortageQuantity, StockAvailabilityStatus status, Instant observedAt) {
        this.stockItemId = stockItemId;
        this.requestedQuantity = requestedQuantity;
        this.observedAvailableQuantity = observedAvailableQuantity;
        this.shortageQuantity = shortageQuantity;
        this.status = status;
        this.observedAt = observedAt;
    }

    void setEstimateLine(EstimateLineJpaEntity estimateLine) {
        this.estimateLine = estimateLine;
        this.estimateLineId = estimateLine.getId();
    }
    public UUID getStockItemId() { return stockItemId; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public int getObservedAvailableQuantity() { return observedAvailableQuantity; }
    public int getShortageQuantity() { return shortageQuantity; }
    public StockAvailabilityStatus getStatus() { return status; }
    public Instant getObservedAt() { return observedAt; }
}
