package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilityStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;
import java.util.UUID;

@Embeddable
public class StockAvailabilitySnapshotEmbeddable {

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

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

    protected StockAvailabilitySnapshotEmbeddable() {
    }

    public StockAvailabilitySnapshotEmbeddable(
            UUID stockItemId, int requestedQuantity, int observedAvailableQuantity, int shortageQuantity,
            StockAvailabilityStatus status, Instant observedAt) {
        this.stockItemId = stockItemId;
        this.requestedQuantity = requestedQuantity;
        this.observedAvailableQuantity = observedAvailableQuantity;
        this.shortageQuantity = shortageQuantity;
        this.status = status;
        this.observedAt = observedAt;
    }

    public UUID getStockItemId() { return stockItemId; }
    public int getRequestedQuantity() { return requestedQuantity; }
    public int getObservedAvailableQuantity() { return observedAvailableQuantity; }
    public int getShortageQuantity() { return shortageQuantity; }
    public StockAvailabilityStatus getStatus() { return status; }
    public Instant getObservedAt() { return observedAt; }
}
