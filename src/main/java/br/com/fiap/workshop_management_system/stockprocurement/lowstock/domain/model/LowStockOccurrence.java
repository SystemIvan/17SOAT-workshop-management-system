package br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model;

import java.time.Instant;
import java.util.UUID;

public class LowStockOccurrence {

    private final UUID id;
    private final UUID stockItemId;
    private final UUID purchaseDemandId;
    private final Instant detectedAt;
    private LowStockOccurrenceStatus status;
    private int observedAvailableQuantity;
    private int suggestedQuantity;
    private Instant updatedAt;
    private Instant closedAt;
    private LowStockClosureReason closureReason;

    public static LowStockOccurrence open(UUID id, UUID stockItemId, UUID purchaseDemandId,
                                          int observedAvailableQuantity, int suggestedQuantity, Instant detectedAt) {
        return new LowStockOccurrence(id, stockItemId, purchaseDemandId, LowStockOccurrenceStatus.OPEN,
                observedAvailableQuantity, suggestedQuantity, detectedAt, detectedAt, null, null);
    }

    public static LowStockOccurrence reconstitute(
            UUID id,
            UUID stockItemId,
            UUID purchaseDemandId,
            LowStockOccurrenceStatus status,
            int observedAvailableQuantity,
            int suggestedQuantity,
            Instant detectedAt,
            Instant updatedAt,
            Instant closedAt,
            LowStockClosureReason closureReason) {
        return new LowStockOccurrence(id, stockItemId, purchaseDemandId, status, observedAvailableQuantity,
                suggestedQuantity, detectedAt, updatedAt, closedAt, closureReason);
    }

    private LowStockOccurrence(
            UUID id,
            UUID stockItemId,
            UUID purchaseDemandId,
            LowStockOccurrenceStatus status,
            int observedAvailableQuantity,
            int suggestedQuantity,
            Instant detectedAt,
            Instant updatedAt,
            Instant closedAt,
            LowStockClosureReason closureReason) {
        if (id == null || stockItemId == null || purchaseDemandId == null || status == null || detectedAt == null
                || updatedAt == null) {
            throw new IllegalArgumentException("Low stock occurrence required data must not be null");
        }
        validateQuantities(observedAvailableQuantity, suggestedQuantity);
        if (updatedAt.isBefore(detectedAt)) {
            throw new IllegalArgumentException("Low stock occurrence timeline must be monotonic");
        }
        validateClosure(status, updatedAt, closedAt, closureReason);
        this.id = id;
        this.stockItemId = stockItemId;
        this.purchaseDemandId = purchaseDemandId;
        this.status = status;
        this.observedAvailableQuantity = observedAvailableQuantity;
        this.suggestedQuantity = suggestedQuantity;
        this.detectedAt = detectedAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
        this.closureReason = closureReason;
    }

    public boolean updateObservation(int availableQuantity, int newSuggestedQuantity, Instant observedAt) {
        requireOpen();
        validateQuantities(availableQuantity, newSuggestedQuantity);
        requireMonotonic(observedAt);
        if (observedAvailableQuantity == availableQuantity && suggestedQuantity == newSuggestedQuantity) {
            return false;
        }
        observedAvailableQuantity = availableQuantity;
        suggestedQuantity = newSuggestedQuantity;
        updatedAt = observedAt;
        return true;
    }

    public boolean close(LowStockClosureReason reason, Instant closedAt) {
        if (status == LowStockOccurrenceStatus.CLOSED) {
            return false;
        }
        if (reason == null) {
            throw new IllegalArgumentException("Low stock closure reason must not be null");
        }
        requireMonotonic(closedAt);
        status = LowStockOccurrenceStatus.CLOSED;
        updatedAt = closedAt;
        this.closedAt = closedAt;
        closureReason = reason;
        return true;
    }

    private void requireOpen() {
        if (status != LowStockOccurrenceStatus.OPEN) {
            throw new IllegalStateException("Closed low stock occurrence cannot be changed");
        }
    }

    private void requireMonotonic(Instant instant) {
        if (instant == null || instant.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Low stock occurrence timeline must be monotonic");
        }
    }

    private static void validateQuantities(int availableQuantity, int suggestedQuantity) {
        if (availableQuantity < 0 || suggestedQuantity <= 0) {
            throw new IllegalArgumentException("Low stock occurrence quantities are invalid");
        }
    }

    private static void validateClosure(LowStockOccurrenceStatus status, Instant updatedAt, Instant closedAt,
                                        LowStockClosureReason closureReason) {
        boolean closed = status == LowStockOccurrenceStatus.CLOSED;
        if (closed && (closedAt == null || closureReason == null)) {
            throw new IllegalArgumentException("Low stock occurrence closure data is inconsistent");
        }
        if (!closed && (closedAt != null || closureReason != null)) {
            throw new IllegalArgumentException("Open low stock occurrence must not have closure data");
        }
        if (closed && (!closedAt.equals(updatedAt))) {
            throw new IllegalArgumentException("Closed occurrence must use the closure time as its last update");
        }
    }

    public UUID id() { return id; }
    public UUID stockItemId() { return stockItemId; }
    public UUID purchaseDemandId() { return purchaseDemandId; }
    public LowStockOccurrenceStatus status() { return status; }
    public int observedAvailableQuantity() { return observedAvailableQuantity; }
    public int suggestedQuantity() { return suggestedQuantity; }
    public Instant detectedAt() { return detectedAt; }
    public Instant updatedAt() { return updatedAt; }
    public Instant closedAt() { return closedAt; }
    public LowStockClosureReason closureReason() { return closureReason; }
}
