package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.service;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilityStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class EstimateExpirationPolicy {

    private static final Duration FULLY_AVAILABLE_WINDOW =
            Duration.ofHours(24);

    private static final Duration UNAVAILABLE_WINDOW =
            Duration.ofHours(48);

    public Instant calculateExpiresAt(
            Instant startsAt,
            List<EstimateLine> lines) {

        Objects.requireNonNull(
                startsAt,
                "startsAt must not be null"
        );

        Objects.requireNonNull(
                lines,
                "lines must not be null"
        );

        boolean hasUnavailableStock = lines.stream()
                .flatMap(line -> line.stockAvailability().stream())
                .anyMatch(availability ->
                        availability.status()
                                != StockAvailabilityStatus.AVAILABLE
                );

        Duration validity = hasUnavailableStock
                ? UNAVAILABLE_WINDOW
                : FULLY_AVAILABLE_WINDOW;

        return startsAt.plus(validity);
    }
}