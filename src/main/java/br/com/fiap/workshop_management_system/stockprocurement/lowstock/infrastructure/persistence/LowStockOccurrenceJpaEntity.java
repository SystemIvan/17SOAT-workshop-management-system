package br.com.fiap.workshop_management_system.stockprocurement.lowstock.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockClosureReason;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "low_stock_occurrences")
public class LowStockOccurrenceJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;
    @Column(name = "purchase_demand_id", nullable = false)
    private UUID purchaseDemandId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LowStockOccurrenceStatus status;
    @Column(name = "open_slot")
    private Integer openSlot;
    @Column(name = "observed_available_quantity", nullable = false)
    private int observedAvailableQuantity;
    @Column(name = "suggested_quantity", nullable = false)
    private int suggestedQuantity;
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "closure_reason", length = 48)
    private LowStockClosureReason closureReason;

    protected LowStockOccurrenceJpaEntity() {
    }

    public LowStockOccurrenceJpaEntity(UUID id, UUID stockItemId, UUID purchaseDemandId,
                                       LowStockOccurrenceStatus status, Integer openSlot,
                                       int observedAvailableQuantity, int suggestedQuantity, Instant detectedAt,
                                       Instant updatedAt, Instant closedAt, LowStockClosureReason closureReason) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.purchaseDemandId = purchaseDemandId;
        this.status = status;
        this.openSlot = openSlot;
        this.observedAvailableQuantity = observedAvailableQuantity;
        this.suggestedQuantity = suggestedQuantity;
        this.detectedAt = detectedAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
        this.closureReason = closureReason;
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return false; }

    public UUID getStockItemId() { return stockItemId; }
    public UUID getPurchaseDemandId() { return purchaseDemandId; }
    public LowStockOccurrenceStatus getStatus() { return status; }
    public Integer getOpenSlot() { return openSlot; }
    public int getObservedAvailableQuantity() { return observedAvailableQuantity; }
    public int getSuggestedQuantity() { return suggestedQuantity; }
    public Instant getDetectedAt() { return detectedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getClosedAt() { return closedAt; }
    public LowStockClosureReason getClosureReason() { return closureReason; }
}
