package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockReceiptTest {

    @Test
    void createsAnImmutableReceiptWithConsistentMovementLines() {
        StockReceiptLine line = line();
        StockReceipt receipt = StockReceipt.create(UUID.randomUUID(), UUID.randomUUID(),
                Instant.parse("2026-08-25T12:00:00.123456789Z"), List.of(line));

        assertEquals(Instant.parse("2026-08-25T12:00:00.123456Z"), receipt.receivedAt());
        assertEquals(line, receipt.lines().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> receipt.lines().add(line));
    }

    @Test
    void rejectsMissingDuplicateOrInconsistentLines() {
        StockReceiptLine line = line();
        assertThrows(IllegalArgumentException.class,
                () -> StockReceipt.create(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> StockReceipt.create(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), List.of(line, line)));
        assertThrows(IllegalArgumentException.class,
                () -> new StockReceiptLine(UUID.randomUUID(), UUID.randomUUID(), 2, 3, 4));
    }

    private StockReceiptLine line() {
        return new StockReceiptLine(UUID.randomUUID(), UUID.randomUUID(), 2, 3, 5);
    }
}
