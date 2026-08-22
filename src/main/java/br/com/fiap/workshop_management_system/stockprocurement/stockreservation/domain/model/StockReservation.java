package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class StockReservation {

    private final UUID id;
    private final UUID serviceExecutionId;
    private final List<StockReservationLine> lines;
    private final Instant createdAt;

    private StockReservationStatus status;
    private Instant consumedAt;

    public static StockReservation create(
            UUID serviceExecutionId,
            List<StockReservationLine> lines,
            Instant createdAt) {
        return new StockReservation(
                UUID.randomUUID(),
                serviceExecutionId,
                lines,
                StockReservationStatus.ACTIVE,
                createdAt,
                null);
    }

    public static StockReservation reconstitute(
            UUID id,
            UUID serviceExecutionId,
            List<StockReservationLine> lines,
            StockReservationStatus status,
            Instant createdAt,
            Instant consumedAt) {
        return new StockReservation(id, serviceExecutionId, lines, status, createdAt, consumedAt);
    }

    private StockReservation(
            UUID id,
            UUID serviceExecutionId,
            List<StockReservationLine> lines,
            StockReservationStatus status,
            Instant createdAt,
            Instant consumedAt) {
        if (id == null || serviceExecutionId == null || status == null || createdAt == null) {
            throw new IllegalArgumentException("Reservation required data must not be null");
        }
        this.id = id;
        this.serviceExecutionId = serviceExecutionId;
        this.lines = validateLines(lines);
        this.status = status;
        this.createdAt = createdAt;
        validateConsumption(status, consumedAt);
        this.consumedAt = consumedAt;
    }

    public void consume(Instant consumedAt) {
        Objects.requireNonNull(consumedAt, "Reservation consumption time must not be null");
        if (status == StockReservationStatus.CONSUMED) {
            return;
        }
        this.status = StockReservationStatus.CONSUMED;
        this.consumedAt = consumedAt;
    }

    private static List<StockReservationLine> validateLines(List<StockReservationLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("A reservation must contain at least one line");
        }
        List<StockReservationLine> copiedLines = List.copyOf(lines);
        Set<UUID> stockItemIds = new HashSet<>();
        for (StockReservationLine line : copiedLines) {
            if (line == null || !stockItemIds.add(line.stockItemId())) {
                throw new IllegalArgumentException("A reservation cannot contain repeated stock items");
            }
        }
        return copiedLines;
    }

    private static void validateConsumption(StockReservationStatus status, Instant consumedAt) {
        if (status == StockReservationStatus.ACTIVE && consumedAt != null) {
            throw new IllegalArgumentException("An active reservation must not have a consumption time");
        }
        if (status == StockReservationStatus.CONSUMED && consumedAt == null) {
            throw new IllegalArgumentException("A consumed reservation must have a consumption time");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID serviceExecutionId() {
        return serviceExecutionId;
    }

    public List<StockReservationLine> lines() {
        return lines;
    }

    public StockReservationStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant consumedAt() {
        return consumedAt;
    }
}
