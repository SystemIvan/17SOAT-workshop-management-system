package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrderJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "payload_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String payloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurchaseOrderStatus status;

    @Column(name = "external_reference", unique = true, length = 255)
    private String externalReference;

    @Column(name = "supplier_rejection_code", length = 64)
    private String supplierRejectionCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "opened_at")
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_user_account_id")
    private UUID closedByUserAccountId;

    @ElementCollection
    @CollectionTable(name = "purchase_order_lines", joinColumns = @JoinColumn(name = "purchase_order_id"))
    private List<PurchaseOrderLineEmbeddable> lines = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "purchase_order_demand_links", joinColumns = @JoinColumn(name = "purchase_order_id"))
    @Column(name = "purchase_demand_id", nullable = false)
    private Set<UUID> selectedDemandIds = new LinkedHashSet<>();

    protected PurchaseOrderJpaEntity() {
    }

    public PurchaseOrderJpaEntity(
            UUID id,
            UUID idempotencyKey,
            String payloadHash,
            PurchaseOrderStatus status,
            String externalReference,
            String supplierRejectionCode,
            Instant createdAt,
            Instant updatedAt,
            Instant openedAt,
            Instant closedAt,
            UUID closedByUserAccountId,
            List<PurchaseOrderLineEmbeddable> lines,
            Set<UUID> selectedDemandIds) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.payloadHash = payloadHash;
        this.status = status;
        this.externalReference = externalReference;
        this.supplierRejectionCode = supplierRejectionCode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.openedAt = openedAt;
        this.closedAt = closedAt;
        this.closedByUserAccountId = closedByUserAccountId;
        this.lines = lines;
        this.selectedDemandIds = selectedDemandIds;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public String getExternalReference() {
        return externalReference;
    }

    public String getSupplierRejectionCode() {
        return supplierRejectionCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public UUID getClosedByUserAccountId() {
        return closedByUserAccountId;
    }

    public List<PurchaseOrderLineEmbeddable> getLines() {
        return lines;
    }

    public Set<UUID> getSelectedDemandIds() {
        return selectedDemandIds;
    }
}
