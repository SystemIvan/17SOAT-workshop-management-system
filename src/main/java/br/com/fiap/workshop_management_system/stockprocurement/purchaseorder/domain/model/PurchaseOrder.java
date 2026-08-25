package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class PurchaseOrder {

    private static final Pattern SHA_256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    private final UUID id;
    private final UUID idempotencyKey;
    private final String payloadHash;
    private final List<PurchaseOrderLine> lines;
    private final Set<UUID> selectedDemandIds;
    private final Instant createdAt;

    private PurchaseOrderStatus status;
    private String externalReference;
    private String supplierRejectionCode;
    private Instant updatedAt;
    private Instant openedAt;

    public static PurchaseOrder prepare(
            UUID idempotencyKey,
            String payloadHash,
            List<PurchaseOrderLine> lines,
            Set<UUID> selectedDemandIds,
            Instant createdAt) {
        Instant normalizedCreatedAt = normalizeInstant(createdAt, "Purchase order creation time must not be null");
        return new PurchaseOrder(
                UUID.randomUUID(),
                idempotencyKey,
                payloadHash,
                PurchaseOrderStatus.PENDING_SUBMISSION,
                lines,
                selectedDemandIds,
                null,
                null,
                normalizedCreatedAt,
                normalizedCreatedAt,
                null);
    }

    public static PurchaseOrder reconstitute(
            UUID id,
            UUID idempotencyKey,
            String payloadHash,
            PurchaseOrderStatus status,
            List<PurchaseOrderLine> lines,
            Set<UUID> selectedDemandIds,
            String externalReference,
            String supplierRejectionCode,
            Instant createdAt,
            Instant updatedAt,
            Instant openedAt) {
        return new PurchaseOrder(
                id,
                idempotencyKey,
                payloadHash,
                status,
                lines,
                selectedDemandIds,
                externalReference,
                supplierRejectionCode,
                createdAt,
                updatedAt,
                openedAt);
    }

    private PurchaseOrder(
            UUID id,
            UUID idempotencyKey,
            String payloadHash,
            PurchaseOrderStatus status,
            List<PurchaseOrderLine> lines,
            Set<UUID> selectedDemandIds,
            String externalReference,
            String supplierRejectionCode,
            Instant createdAt,
            Instant updatedAt,
            Instant openedAt) {
        if (id == null || idempotencyKey == null || status == null) {
            throw new IllegalArgumentException("Purchase order required data must not be null");
        }
        if (payloadHash == null || !SHA_256_PATTERN.matcher(payloadHash).matches()) {
            throw new IllegalArgumentException("Purchase order payload hash must be a lowercase SHA-256 value");
        }
        Instant normalizedCreatedAt = normalizeInstant(createdAt, "Purchase order creation time must not be null");
        Instant normalizedUpdatedAt = normalizeInstant(updatedAt, "Purchase order update time must not be null");
        Instant normalizedOpenedAt = openedAt == null ? null : normalizeInstant(openedAt, "");
        if (normalizedUpdatedAt.isBefore(normalizedCreatedAt)
                || (normalizedOpenedAt != null && normalizedOpenedAt.isBefore(normalizedCreatedAt))) {
            throw new IllegalArgumentException("Purchase order timestamps are inconsistent");
        }

        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
        this.status = status;
        this.lines = validateLines(lines);
        this.selectedDemandIds = validateDemandIds(selectedDemandIds);
        this.externalReference = normalizeOptional(externalReference, 255, "external reference");
        this.supplierRejectionCode = normalizeOptional(supplierRejectionCode, 64, "supplier rejection code");
        this.createdAt = normalizedCreatedAt;
        this.updatedAt = normalizedUpdatedAt;
        this.openedAt = normalizedOpenedAt;
        validateState();
    }

    public void open(String externalReference, Instant openedAt) {
        String normalizedReference = normalizeRequired(externalReference, 255, "external reference");
        if (status == PurchaseOrderStatus.OPEN && normalizedReference.equals(this.externalReference)) {
            return;
        }
        if (status != PurchaseOrderStatus.PENDING_SUBMISSION) {
            throw new PurchaseOrderTransitionException("Purchase order cannot be opened from its current status");
        }
        Instant normalizedOpenedAt = requireCurrentOrFuture(openedAt);
        this.status = PurchaseOrderStatus.OPEN;
        this.externalReference = normalizedReference;
        this.supplierRejectionCode = null;
        this.updatedAt = normalizedOpenedAt;
        this.openedAt = normalizedOpenedAt;
    }

    public void reject(String rejectionCode, Instant rejectedAt) {
        String normalizedCode = normalizeRequired(rejectionCode, 64, "supplier rejection code");
        if (status == PurchaseOrderStatus.REJECTED && normalizedCode.equals(supplierRejectionCode)) {
            return;
        }
        if (status != PurchaseOrderStatus.PENDING_SUBMISSION) {
            throw new PurchaseOrderTransitionException("Purchase order cannot be rejected from its current status");
        }
        this.updatedAt = requireCurrentOrFuture(rejectedAt);
        this.status = PurchaseOrderStatus.REJECTED;
        this.externalReference = null;
        this.supplierRejectionCode = normalizedCode;
        this.openedAt = null;
    }

    private Instant requireCurrentOrFuture(Instant value) {
        Instant normalized = normalizeInstant(value, "Purchase order transition time must not be null");
        if (normalized.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Purchase order transition time must not precede the last update");
        }
        return normalized;
    }

    private void validateState() {
        switch (status) {
            case PENDING_SUBMISSION -> {
                if (externalReference != null || supplierRejectionCode != null || openedAt != null) {
                    throw new IllegalArgumentException("Pending purchase order has inconsistent state data");
                }
            }
            case OPEN -> {
                if (externalReference == null || supplierRejectionCode != null || openedAt == null) {
                    throw new IllegalArgumentException("Open purchase order has inconsistent state data");
                }
            }
            case REJECTED -> {
                if (externalReference != null || supplierRejectionCode == null || openedAt != null) {
                    throw new IllegalArgumentException("Rejected purchase order has inconsistent state data");
                }
            }
        }
    }

    private static List<PurchaseOrderLine> validateLines(List<PurchaseOrderLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new IllegalArgumentException("Purchase order must contain between 1 and 100 lines");
        }
        List<PurchaseOrderLine> copiedLines = List.copyOf(lines);
        Set<UUID> stockItemIds = new HashSet<>();
        for (PurchaseOrderLine line : copiedLines) {
            if (line == null || !stockItemIds.add(line.stockItemId())) {
                throw new IllegalArgumentException("Purchase order cannot contain repeated stock items");
            }
        }
        return copiedLines;
    }

    private static Set<UUID> validateDemandIds(Set<UUID> selectedDemandIds) {
        if (selectedDemandIds == null || selectedDemandIds.size() > 100) {
            throw new IllegalArgumentException("Purchase order demand selection is invalid");
        }
        for (UUID demandId : selectedDemandIds) {
            if (demandId == null) {
                throw new IllegalArgumentException("Purchase order demand selection is invalid");
            }
        }
        return Set.copyOf(new LinkedHashSet<>(selectedDemandIds));
    }

    private static String normalizeOptional(String value, int maxLength, String field) {
        return value == null ? null : normalizeRequired(value, maxLength, field);
    }

    private static String normalizeRequired(String value, int maxLength, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Purchase order " + field + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException("Purchase order " + field + " is invalid");
        }
        return normalized;
    }

    private static Instant normalizeInstant(Instant value, String message) {
        return Objects.requireNonNull(value, message).truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() {
        return id;
    }

    public UUID idempotencyKey() {
        return idempotencyKey;
    }

    public String payloadHash() {
        return payloadHash;
    }

    public PurchaseOrderStatus status() {
        return status;
    }

    public List<PurchaseOrderLine> lines() {
        return lines;
    }

    public Set<UUID> selectedDemandIds() {
        return selectedDemandIds;
    }

    public String externalReference() {
        return externalReference;
    }

    public String supplierRejectionCode() {
        return supplierRejectionCode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant openedAt() {
        return openedAt;
    }
}
