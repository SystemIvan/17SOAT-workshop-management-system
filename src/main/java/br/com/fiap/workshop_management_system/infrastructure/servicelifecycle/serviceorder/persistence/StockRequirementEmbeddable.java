package br.com.fiap.workshop_management_system.infrastructure.servicelifecycle.serviceorder.persistence;

import br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.StockItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA projection of {@link br.com.fiap.workshop_management_system.domain.servicelifecycle.serviceorder.model.StockRequirement}.
 */
@Embeddable
public class StockRequirementEmbeddable {

    private UUID stockItemId;

    @Enumerated(EnumType.STRING)
    private StockItemType type;

    private int quantity;

    private String nameSnapshot;

    @Column(name = "price_snapshot_value")
    private BigDecimal priceSnapshotValue;

    @Column(name = "price_snapshot_currency")
    private String priceSnapshotCurrency;

    private boolean reserved;

    protected StockRequirementEmbeddable() {
    }

    public StockRequirementEmbeddable(
            UUID stockItemId,
            StockItemType type,
            int quantity,
            String nameSnapshot,
            BigDecimal priceSnapshotValue,
            String priceSnapshotCurrency,
            boolean reserved) {
        this.stockItemId = stockItemId;
        this.type = type;
        this.quantity = quantity;
        this.nameSnapshot = nameSnapshot;
        this.priceSnapshotValue = priceSnapshotValue;
        this.priceSnapshotCurrency = priceSnapshotCurrency;
        this.reserved = reserved;
    }

    public UUID getStockItemId() {
        return stockItemId;
    }

    public StockItemType getType() {
        return type;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public BigDecimal getPriceSnapshotValue() {
        return priceSnapshotValue;
    }

    public String getPriceSnapshotCurrency() {
        return priceSnapshotCurrency;
    }

    public boolean isReserved() {
        return reserved;
    }
}
