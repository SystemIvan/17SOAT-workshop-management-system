package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class StockReceipt {

    private final UUID id;
    private final UUID purchaseOrderId;
    private final UUID receivedByUserAccountId;
    private final Instant receivedAt;
    private final List<StockReceiptLine> lines;

    public static StockReceipt create(
            UUID purchaseOrderId,
            UUID receivedByUserAccountId,
            Instant receivedAt,
            List<StockReceiptLine> lines) {
        return new StockReceipt(UUID.randomUUID(), purchaseOrderId, receivedByUserAccountId, receivedAt, lines);
    }

    public static StockReceipt reconstitute(
            UUID id,
            UUID purchaseOrderId,
            UUID receivedByUserAccountId,
            Instant receivedAt,
            List<StockReceiptLine> lines) {
        return new StockReceipt(id, purchaseOrderId, receivedByUserAccountId, receivedAt, lines);
    }

    private StockReceipt(
            UUID id,
            UUID purchaseOrderId,
            UUID receivedByUserAccountId,
            Instant receivedAt,
            List<StockReceiptLine> lines) {
        if (id == null || purchaseOrderId == null || receivedByUserAccountId == null) {
            throw new IllegalArgumentException("Stock receipt required data must not be null");
        }
        this.id = id;
        this.purchaseOrderId = purchaseOrderId;
        this.receivedByUserAccountId = receivedByUserAccountId;
        this.receivedAt = Objects.requireNonNull(receivedAt, "Stock receipt time must not be null")
                .truncatedTo(ChronoUnit.MICROS);
        this.lines = validateLines(lines);
    }

    private static List<StockReceiptLine> validateLines(List<StockReceiptLine> lines) {
        if (lines == null || lines.isEmpty() || lines.size() > 100) {
            throw new IllegalArgumentException("Stock receipt must contain between 1 and 100 lines");
        }
        List<StockReceiptLine> copiedLines = List.copyOf(lines);
        Set<UUID> stockItemIds = new HashSet<>();
        Set<UUID> movementIds = new HashSet<>();
        for (StockReceiptLine line : copiedLines) {
            if (line == null || !stockItemIds.add(line.stockItemId()) || !movementIds.add(line.movementId())) {
                throw new IllegalArgumentException("Stock receipt cannot contain repeated items or movements");
            }
        }
        return copiedLines;
    }

    public UUID id() {
        return id;
    }

    public UUID purchaseOrderId() {
        return purchaseOrderId;
    }

    public UUID receivedByUserAccountId() {
        return receivedByUserAccountId;
    }

    public Instant receivedAt() {
        return receivedAt;
    }

    public List<StockReceiptLine> lines() {
        return lines;
    }
}
