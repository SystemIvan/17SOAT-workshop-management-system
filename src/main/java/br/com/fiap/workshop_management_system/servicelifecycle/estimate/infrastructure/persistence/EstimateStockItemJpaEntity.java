package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockItemType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "estimate_stock_items")
public class EstimateStockItemJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimate_line_id", nullable = false)
    private EstimateLineJpaEntity estimateLine;

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StockItemType type;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "name_snapshot", nullable = false, length = 255)
    private String nameSnapshot;

    @Column(name = "price_snapshot_value", nullable = false, precision = 38, scale = 2)
    private BigDecimal priceSnapshotValue;

    @Column(name = "price_snapshot_currency", nullable = false, length = 3)
    private String priceSnapshotCurrency;

    protected EstimateStockItemJpaEntity() {
    }

    public EstimateStockItemJpaEntity(
            UUID id,
            UUID stockItemId,
            StockItemType type,
            int quantity,
            String nameSnapshot,
            BigDecimal priceSnapshotValue,
            String priceSnapshotCurrency) {
        this.id = id;
        this.stockItemId = stockItemId;
        this.type = type;
        this.quantity = quantity;
        this.nameSnapshot = nameSnapshot;
        this.priceSnapshotValue = priceSnapshotValue;
        this.priceSnapshotCurrency = priceSnapshotCurrency;
    }

    void setEstimateLine(EstimateLineJpaEntity estimateLine) {
        this.estimateLine = estimateLine;
    }

    public UUID getId() { return id; }
    public UUID getStockItemId() { return stockItemId; }
    public StockItemType getType() { return type; }
    public int getQuantity() { return quantity; }
    public String getNameSnapshot() { return nameSnapshot; }
    public BigDecimal getPriceSnapshotValue() { return priceSnapshotValue; }
    public String getPriceSnapshotCurrency() { return priceSnapshotCurrency; }
}