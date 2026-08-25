package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class EstimateStockAvailabilityJpaEntityId implements Serializable {

    private UUID estimateLineId;
    private UUID stockItemId;

    public EstimateStockAvailabilityJpaEntityId() { }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EstimateStockAvailabilityJpaEntityId that)) {
            return false;
        }
        return Objects.equals(estimateLineId, that.estimateLineId) && Objects.equals(stockItemId, that.stockItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(estimateLineId, stockItemId);
    }
}
