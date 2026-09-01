package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

import java.util.Objects;

public record StockItemReservationAssessment(
        StockItemReservationEligibility eligibility,
        Quantity availableQuantity
) {

    public StockItemReservationAssessment {
        Objects.requireNonNull(eligibility, "Reservation eligibility must not be null");
        Objects.requireNonNull(availableQuantity, "Available quantity must not be null");
    }

    public boolean eligible() {
        return eligibility == StockItemReservationEligibility.ELIGIBLE;
    }
}
