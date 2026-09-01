package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.service;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockAvailability;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilityStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstimateExpirationPolicyTest {

    private static final Instant STARTS_AT =
            Instant.parse("2026-08-25T15:00:00Z");

    @Test
    void expiresIn24HoursWhenAllStockItemsAreAvailable() {
        EstimateLine line = newLine(
                new EstimateStockAvailability(
                        UUID.randomUUID(),
                        2,
                        2,
                        0,
                        StockAvailabilityStatus.AVAILABLE,
                        STARTS_AT
                )
        );

        EstimateExpirationPolicy policy =
                new EstimateExpirationPolicy();

        Instant expiresAt =
                policy.calculateExpiresAt(STARTS_AT, List.of(line));

        assertEquals(
                STARTS_AT.plusSeconds(24 * 60 * 60),
                expiresAt
        );
    }

    @Test
    void expiresIn48HoursWhenAnyStockItemIsUnavailable() {
        EstimateLine availableLine = newLine(
                new EstimateStockAvailability(
                        UUID.randomUUID(),
                        1,
                        1,
                        0,
                        StockAvailabilityStatus.AVAILABLE,
                        STARTS_AT
                )
        );

        EstimateLine unavailableLine = newLine(
                new EstimateStockAvailability(
                        UUID.randomUUID(),
                        3,
                        1,
                        2,
                        StockAvailabilityStatus.INSUFFICIENT_QUANTITY,
                        STARTS_AT
                )
        );

        EstimateExpirationPolicy policy =
                new EstimateExpirationPolicy();

        Instant expiresAt =
                policy.calculateExpiresAt(
                        STARTS_AT,
                        List.of(availableLine, unavailableLine)
                );

        assertEquals(
                STARTS_AT.plusSeconds(48 * 60 * 60),
                expiresAt
        );
    }

    private EstimateLine newLine(
            EstimateStockAvailability availability) {

        return new EstimateLine(
                UUID.randomUUID(),
                "Troca de oleo",
                Money.brl(new BigDecimal("120.00")),
                List.of(),
                List.of(availability)
        );
    }
}
