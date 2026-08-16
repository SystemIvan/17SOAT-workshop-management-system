package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EstimateGenerated(
        UUID eventId,
        Instant occurredAt,
        UUID estimateId,
        UUID serviceOrderId,
        UUID diagnosisId,
        UUID customerId,
        Instant expiresAt
) {
    public EstimateGenerated {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(estimateId, "estimateId must not be null");
        Objects.requireNonNull(serviceOrderId, "serviceOrderId must not be null");
        Objects.requireNonNull(diagnosisId, "diagnosisId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
    }

    public static EstimateGenerated from(
            UUID estimateId,
            UUID serviceOrderId,
            UUID diagnosisId,
            UUID customerId,
            Instant occurredAt,
            Instant expiresAt) {

        return new EstimateGenerated(
                UUID.randomUUID(),
                occurredAt,
                estimateId,
                serviceOrderId,
                diagnosisId,
                customerId,
                expiresAt
        );
    }
}