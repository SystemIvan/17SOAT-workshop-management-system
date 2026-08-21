package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockReservationTest {

    @Test
    void createsAnActiveReservationWithImmutableUniqueLines() {
        UUID serviceExecutionId = UUID.randomUUID();
        StockReservationLine line = new StockReservationLine(UUID.randomUUID(), 2);
        Instant createdAt = Instant.parse("2026-08-20T12:00:00Z");

        StockReservation reservation = StockReservation.create(serviceExecutionId, List.of(line), createdAt);

        assertEquals(serviceExecutionId, reservation.serviceExecutionId());
        assertEquals(StockReservationStatus.ACTIVE, reservation.status());
        assertNull(reservation.consumedAt());
        assertThrows(UnsupportedOperationException.class,
                () -> reservation.lines().add(new StockReservationLine(UUID.randomUUID(), 1)));
    }

    @Test
    void rejectsInvalidIdentityLinesAndInconsistentReconstitution() {
        StockReservationLine line = new StockReservationLine(UUID.randomUUID(), 1);

        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.create(null, List.of(line), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.create(UUID.randomUUID(), List.of(), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.create(UUID.randomUUID(), List.of(line, line), Instant.now()));
        assertThrows(IllegalArgumentException.class,
                () -> StockReservation.reconstitute(UUID.randomUUID(), UUID.randomUUID(), List.of(line),
                        StockReservationStatus.CONSUMED, Instant.now(), null));
    }

    @Test
    void consumesOnlyOnceAndPreservesTheFirstTimestamp() {
        StockReservation reservation = StockReservation.create(
                UUID.randomUUID(), List.of(new StockReservationLine(UUID.randomUUID(), 1)), Instant.now());
        Instant firstConsumption = Instant.parse("2026-08-20T12:00:00Z");

        reservation.consume(firstConsumption);
        reservation.consume(firstConsumption.plusSeconds(60));

        assertEquals(StockReservationStatus.CONSUMED, reservation.status());
        assertEquals(firstConsumption, reservation.consumedAt());
    }
}
