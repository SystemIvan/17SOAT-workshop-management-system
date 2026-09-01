package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class PurchaseDemand {

    private final UUID id;
    private final PurchaseDemandOrigin origin;
    private final UUID originReferenceId;
    private final UUID stockItemId;
    private final Instant createdAt;

    private Integer requestedQuantity;
    private int observedAvailableQuantity;
    private int suggestedQuantity;
    private PurchaseDemandStatus status;
    private UUID claimedByPurchaseOrderId;
    private Instant updatedAt;
    private Instant resolvedAt;

    public static PurchaseDemand createPendingRepair(
            UUID serviceExecutionId,
            UUID stockItemId,
            int requestedQuantity,
            int observedAvailableQuantity,
            int suggestedQuantity,
            Instant createdAt) {
        return create(
                PurchaseDemandOrigin.PENDING_REPAIR,
                serviceExecutionId,
                stockItemId,
                requestedQuantity,
                observedAvailableQuantity,
                suggestedQuantity,
                createdAt);
    }

    public static PurchaseDemand createLowStock(
            UUID occurrenceId,
            UUID stockItemId,
            int observedAvailableQuantity,
            int suggestedQuantity,
            Instant createdAt) {
        return create(
                PurchaseDemandOrigin.LOW_STOCK,
                occurrenceId,
                stockItemId,
                null,
                observedAvailableQuantity,
                suggestedQuantity,
                createdAt);
    }

    public static PurchaseDemand reconstitute(
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
        return new PurchaseDemand(
                id,
                origin,
                originReferenceId,
                stockItemId,
                requestedQuantity,
                observedAvailableQuantity,
                suggestedQuantity,
                status,
                claimedByPurchaseOrderId,
                createdAt,
                updatedAt,
                resolvedAt);
    }

    private static PurchaseDemand create(
            PurchaseDemandOrigin origin,
            UUID originReferenceId,
            UUID stockItemId,
            Integer requestedQuantity,
            int observedAvailableQuantity,
            int suggestedQuantity,
            Instant createdAt) {
        Instant normalizedCreatedAt = normalizeInstant(createdAt, "Purchase demand creation time must not be null");
        return new PurchaseDemand(
                UUID.randomUUID(),
                origin,
                originReferenceId,
                stockItemId,
                requestedQuantity,
                observedAvailableQuantity,
                suggestedQuantity,
                PurchaseDemandStatus.OPEN,
                null,
                normalizedCreatedAt,
                normalizedCreatedAt,
                null);
    }

    private PurchaseDemand(
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
        if (id == null || origin == null || originReferenceId == null || stockItemId == null || status == null) {
            throw new IllegalArgumentException("Purchase demand required data must not be null");
        }
        validateQuantities(origin, requestedQuantity, observedAvailableQuantity, suggestedQuantity);
        Instant normalizedCreatedAt = normalizeInstant(createdAt, "Purchase demand creation time must not be null");
        Instant normalizedUpdatedAt = normalizeInstant(updatedAt, "Purchase demand update time must not be null");
        Instant normalizedResolvedAt = resolvedAt == null ? null : normalizeInstant(resolvedAt, "");
        validateTimeline(normalizedCreatedAt, normalizedUpdatedAt, normalizedResolvedAt);
        validateState(status, claimedByPurchaseOrderId, normalizedResolvedAt);

        this.id = id;
        this.origin = origin;
        this.originReferenceId = originReferenceId;
        this.stockItemId = stockItemId;
        this.requestedQuantity = requestedQuantity;
        this.observedAvailableQuantity = observedAvailableQuantity;
        this.suggestedQuantity = suggestedQuantity;
        this.status = status;
        this.claimedByPurchaseOrderId = claimedByPurchaseOrderId;
        this.createdAt = normalizedCreatedAt;
        this.updatedAt = normalizedUpdatedAt;
        this.resolvedAt = normalizedResolvedAt;
    }

    public void recordObservation(
            Integer requestedQuantity,
            int observedAvailableQuantity,
            int suggestedQuantity,
            Instant observedAt) {
        if (status != PurchaseDemandStatus.OPEN) {
            return;
        }
        validateQuantities(origin, requestedQuantity, observedAvailableQuantity, suggestedQuantity);
        this.updatedAt = requireCurrentOrFuture(observedAt);
        this.requestedQuantity = requestedQuantity;
        this.observedAvailableQuantity = observedAvailableQuantity;
        this.suggestedQuantity = suggestedQuantity;
    }

    public void claim(UUID purchaseOrderId, Instant claimedAt) {
        Objects.requireNonNull(purchaseOrderId, "Purchase order id must not be null");
        if (status == PurchaseDemandStatus.CLAIMED && purchaseOrderId.equals(claimedByPurchaseOrderId)) {
            return;
        }
        if (status != PurchaseDemandStatus.OPEN) {
            throw new PurchaseDemandNotSelectableException();
        }
        this.updatedAt = requireCurrentOrFuture(claimedAt);
        this.status = PurchaseDemandStatus.CLAIMED;
        this.claimedByPurchaseOrderId = purchaseOrderId;
    }

    public void markOrdered(UUID purchaseOrderId, Instant orderedAt) {
        Objects.requireNonNull(purchaseOrderId, "Purchase order id must not be null");
        if (status == PurchaseDemandStatus.ORDERED) {
            return;
        }
        if (status != PurchaseDemandStatus.CLAIMED || !purchaseOrderId.equals(claimedByPurchaseOrderId)) {
            throw new PurchaseDemandNotSelectableException();
        }
        this.updatedAt = requireCurrentOrFuture(orderedAt);
        this.status = PurchaseDemandStatus.ORDERED;
        this.claimedByPurchaseOrderId = null;
    }

    public void release(UUID purchaseOrderId, Instant releasedAt) {
        Objects.requireNonNull(purchaseOrderId, "Purchase order id must not be null");
        if (status == PurchaseDemandStatus.OPEN) {
            return;
        }
        if (status != PurchaseDemandStatus.CLAIMED || !purchaseOrderId.equals(claimedByPurchaseOrderId)) {
            throw new PurchaseDemandNotSelectableException();
        }
        this.updatedAt = requireCurrentOrFuture(releasedAt);
        this.status = PurchaseDemandStatus.OPEN;
        this.claimedByPurchaseOrderId = null;
    }

    public void resolve(Instant resolutionTime) {
        if (status != PurchaseDemandStatus.OPEN) {
            return;
        }
        Instant normalizedResolutionTime = requireCurrentOrFuture(resolutionTime);
        this.status = PurchaseDemandStatus.RESOLVED;
        this.updatedAt = normalizedResolutionTime;
        this.resolvedAt = normalizedResolutionTime;
    }

    private Instant requireCurrentOrFuture(Instant value) {
        Instant normalized = normalizeInstant(value, "Purchase demand transition time must not be null");
        if (normalized.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Purchase demand transition time must not precede the last update");
        }
        return normalized;
    }

    private static void validateQuantities(
            PurchaseDemandOrigin origin,
            Integer requestedQuantity,
            int observedAvailableQuantity,
            int suggestedQuantity) {
        if (observedAvailableQuantity < 0 || suggestedQuantity <= 0) {
            throw new IllegalArgumentException("Purchase demand quantities are invalid");
        }
        if (origin == PurchaseDemandOrigin.PENDING_REPAIR
                && (requestedQuantity == null || requestedQuantity <= 0)) {
            throw new IllegalArgumentException("Pending repair demand requires a positive requested quantity");
        }
        if (origin == PurchaseDemandOrigin.LOW_STOCK && requestedQuantity != null) {
            throw new IllegalArgumentException("Low stock demand must not have a requested quantity");
        }
    }

    private static void validateState(
            PurchaseDemandStatus status,
            UUID claimedByPurchaseOrderId,
            Instant resolvedAt) {
        if ((status == PurchaseDemandStatus.CLAIMED) != (claimedByPurchaseOrderId != null)) {
            throw new IllegalArgumentException("Purchase demand claim data is inconsistent with its status");
        }
        if ((status == PurchaseDemandStatus.RESOLVED) != (resolvedAt != null)) {
            throw new IllegalArgumentException("Purchase demand resolution data is inconsistent with its status");
        }
    }

    private static void validateTimeline(Instant createdAt, Instant updatedAt, Instant resolvedAt) {
        if (updatedAt.isBefore(createdAt) || (resolvedAt != null && resolvedAt.isBefore(createdAt))) {
            throw new IllegalArgumentException("Purchase demand timestamps are inconsistent");
        }
    }

    private static Instant normalizeInstant(Instant value, String message) {
        return Objects.requireNonNull(value, message).truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() {
        return id;
    }

    public PurchaseDemandOrigin origin() {
        return origin;
    }

    public UUID originReferenceId() {
        return originReferenceId;
    }

    public UUID stockItemId() {
        return stockItemId;
    }

    public Integer requestedQuantity() {
        return requestedQuantity;
    }

    public int observedAvailableQuantity() {
        return observedAvailableQuantity;
    }

    public int suggestedQuantity() {
        return suggestedQuantity;
    }

    public PurchaseDemandStatus status() {
        return status;
    }

    public UUID claimedByPurchaseOrderId() {
        return claimedByPurchaseOrderId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant resolvedAt() {
        return resolvedAt;
    }
}
