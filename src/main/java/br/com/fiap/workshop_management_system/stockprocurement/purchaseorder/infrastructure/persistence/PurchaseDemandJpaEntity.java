package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandStatus;
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
@Table(name = "purchase_demands")
public class PurchaseDemandJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseDemandOrigin origin;

    @Column(name = "origin_reference_id", nullable = false)
    private UUID originReferenceId;

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

    @Column(name = "requested_quantity")
    private Integer requestedQuantity;

    @Column(name = "observed_available_quantity", nullable = false)
    private int observedAvailableQuantity;

    @Column(name = "suggested_quantity", nullable = false)
    private int suggestedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseDemandStatus status;

    @Column(name = "claimed_by_purchase_order_id")
    private UUID claimedByPurchaseOrderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected PurchaseDemandJpaEntity() {
    }

    public PurchaseDemandJpaEntity(
            UUID id,
            PurchaseDemandOrigin origin,
            UUID originReferenceId,
            UUID stockItemId,
            Integer requestedQuantity,
            int observedAvailableQuantity,
            int suggestedQuantity,
            PurchaseDemandStatus status,
            UUID claimedByPurchaseOrderId,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt) {
        this.id = id;
        this.origin = origin;
        this.originReferenceId = originReferenceId;
        this.stockItemId = stockItemId;
        this.requestedQuantity = requestedQuantity;
        this.observedAvailableQuantity = observedAvailableQuantity;
        this.suggestedQuantity = suggestedQuantity;
        this.status = status;
        this.claimedByPurchaseOrderId = claimedByPurchaseOrderId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.resolvedAt = resolvedAt;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public PurchaseDemandOrigin getOrigin() {
        return origin;
    }

    public UUID getOriginReferenceId() {
        return originReferenceId;
    }

    public UUID getStockItemId() {
        return stockItemId;
    }

    public Integer getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getObservedAvailableQuantity() {
        return observedAvailableQuantity;
    }

    public int getSuggestedQuantity() {
        return suggestedQuantity;
    }

    public PurchaseDemandStatus getStatus() {
        return status;
    }

    public UUID getClaimedByPurchaseOrderId() {
        return claimedByPurchaseOrderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
