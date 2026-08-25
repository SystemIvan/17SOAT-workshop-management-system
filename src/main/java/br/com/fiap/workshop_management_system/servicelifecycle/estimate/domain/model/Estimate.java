package br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Estimate {

    private final UUID id;
    private final UUID serviceOrderId;
    private final UUID diagnosisId;
    private final UUID customerId;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final List<EstimateLine> lines;
    private EstimateStatus status;

    private Estimate(
            UUID id,
            UUID serviceOrderId,
            UUID diagnosisId,
            UUID customerId,
            Instant createdAt,
            Instant expiresAt,
            List<EstimateLine> lines,
            EstimateStatus status) {

        this.id = Objects.requireNonNull(id, "id must not be null");
        this.serviceOrderId = Objects.requireNonNull(serviceOrderId, "serviceOrderId must not be null");
        this.diagnosisId = Objects.requireNonNull(diagnosisId, "diagnosisId must not be null");
        this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.expiresAt = expiresAt;
        this.status = Objects.requireNonNull(status, "status must not be null");

        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Estimate must contain at least one line");
        }

        this.lines = List.copyOf(lines);
    }

    public static Estimate create(
            UUID serviceOrderId,
            UUID diagnosisId,
            UUID customerId,
            Instant createdAt,
            Instant expiresAt,
            List<EstimateLine> lines) {

        return new Estimate(
                UUID.randomUUID(),
                serviceOrderId,
                diagnosisId,
                customerId,
                createdAt,
                expiresAt,
                lines,
                EstimateStatus.DRAFT
        );
    }

    public static Estimate reconstitute(
            UUID id,
            UUID serviceOrderId,
            UUID diagnosisId,
            UUID customerId,
            Instant createdAt,
            Instant expiresAt,
            List<EstimateLine> lines,
            EstimateStatus status) {

        return new Estimate(
                id,
                serviceOrderId,
                diagnosisId,
                customerId,
                createdAt,
                expiresAt,
                lines,
                status
        );
    }

    public void markSent() {
        if (status != EstimateStatus.DRAFT) {
            throw new IllegalStateException(
                    "Estimate must be DRAFT to be sent: " + id
            );
        }

        status = EstimateStatus.SENT;
    }

    public void close() {
        if (status != EstimateStatus.SENT) {
            throw new IllegalStateException(
                    "Estimate must be SENT to be closed: " + id
            );
        }

        status = EstimateStatus.CLOSED;
    }

    public void expire() {
        if (status != EstimateStatus.SENT) {
            throw new IllegalStateException(
                    "Estimate must be SENT to expire: " + id
            );
        }

        status = EstimateStatus.EXPIRED;
    }

    public UUID id() {
        return id;
    }

    public UUID serviceOrderId() {
        return serviceOrderId;
    }

    public UUID diagnosisId() {
        return diagnosisId;
    }

    public UUID customerId() {
        return customerId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public List<EstimateLine> lines() {
        return lines;
    }

    public EstimateStatus status() {
        return status;
    }
}