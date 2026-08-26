package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_receipts")
public class StockReceiptJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "purchase_order_id", nullable = false, unique = true)
    private UUID purchaseOrderId;

    @Column(name = "received_by_user_account_id", nullable = false)
    private UUID receivedByUserAccountId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @ElementCollection
    @CollectionTable(name = "stock_receipt_lines", joinColumns = @JoinColumn(name = "stock_receipt_id"))
    private List<StockReceiptLineEmbeddable> lines = new ArrayList<>();

    protected StockReceiptJpaEntity() {
    }

    public StockReceiptJpaEntity(
            UUID id,
            UUID purchaseOrderId,
            UUID receivedByUserAccountId,
            Instant receivedAt,
            List<StockReceiptLineEmbeddable> lines) {
        this.id = id;
        this.purchaseOrderId = purchaseOrderId;
        this.receivedByUserAccountId = receivedByUserAccountId;
        this.receivedAt = receivedAt;
        this.lines = lines;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    public UUID getPurchaseOrderId() {
        return purchaseOrderId;
    }

    public UUID getReceivedByUserAccountId() {
        return receivedByUserAccountId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public List<StockReceiptLineEmbeddable> getLines() {
        return lines;
    }
}
