package br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowStockOccurrenceTest {

    private static final Instant DETECTED_AT = Instant.parse("2026-08-26T17:00:00Z");

    @Test
    void opensUpdatesAndClosesTerminally() {
        LowStockOccurrence occurrence = LowStockOccurrence.open(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                4, 8, DETECTED_AT);
        Instant updatedAt = DETECTED_AT.plusSeconds(1);
        Instant closedAt = updatedAt.plusSeconds(1);

        assertTrue(occurrence.updateObservation(2, 10, updatedAt));
        assertFalse(occurrence.updateObservation(2, 10, updatedAt));
        assertTrue(occurrence.close(LowStockClosureReason.STOCK_RECOVERED, closedAt));
        assertFalse(occurrence.close(LowStockClosureReason.POLICY_DISABLED, closedAt.plusSeconds(1)));
        assertEquals(LowStockOccurrenceStatus.CLOSED, occurrence.status());
        assertEquals(LowStockClosureReason.STOCK_RECOVERED, occurrence.closureReason());
        assertEquals(closedAt, occurrence.closedAt());
        assertThrows(IllegalStateException.class, () -> occurrence.updateObservation(1, 11, closedAt.plusSeconds(1)));
    }

    @Test
    void rejectsInvalidReconstitutedStateAndTimeline() {
        UUID id = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID demandId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> LowStockOccurrence.open(id, itemId, demandId, -1, 1, DETECTED_AT));
        assertThrows(IllegalArgumentException.class, () -> LowStockOccurrence.reconstitute(
                id, itemId, demandId, LowStockOccurrenceStatus.OPEN, 1, 2, DETECTED_AT, DETECTED_AT, DETECTED_AT, null));
        assertThrows(IllegalArgumentException.class, () -> LowStockOccurrence.reconstitute(
                id, itemId, demandId, LowStockOccurrenceStatus.CLOSED, 1, 2, DETECTED_AT,
                DETECTED_AT.plusSeconds(1), DETECTED_AT, LowStockClosureReason.STOCK_RECOVERED));
    }
}
