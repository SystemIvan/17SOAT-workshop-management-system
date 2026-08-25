package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItemType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseOrderTest {

    private static final String PAYLOAD_HASH = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-08-24T15:30:00.123456789Z");

    @Test
    void preparesAnImmutablePendingSubmissionWithSnapshots() {
        UUID demandId = UUID.randomUUID();
        PurchaseOrder order = PurchaseOrder.prepare(
                UUID.randomUUID(), PAYLOAD_HASH, List.of(line()), Set.of(demandId), CREATED_AT);

        assertEquals(PurchaseOrderStatus.PENDING_SUBMISSION, order.status());
        assertEquals("OIL-FILTER-001", order.lines().getFirst().skuSnapshot());
        assertEquals(Set.of(demandId), order.selectedDemandIds());
        assertNull(order.externalReference());
        assertThrows(UnsupportedOperationException.class, () -> order.lines().add(line()));
        assertThrows(UnsupportedOperationException.class, () -> order.selectedDemandIds().add(UUID.randomUUID()));
    }

    @Test
    void opensIdempotentlyOnlyWithTheSameExternalReference() {
        PurchaseOrder order = preparedOrder();
        Instant openedAt = CREATED_AT.plusSeconds(1);

        order.open(" SUP-123 ", openedAt);
        order.open("SUP-123", openedAt.plusSeconds(1));

        assertEquals(PurchaseOrderStatus.OPEN, order.status());
        assertEquals("SUP-123", order.externalReference());
        assertEquals(openedAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS), order.openedAt());
        assertThrows(PurchaseOrderTransitionException.class,
                () -> order.open("SUP-456", openedAt.plusSeconds(2)));
        assertThrows(PurchaseOrderTransitionException.class,
                () -> order.reject("PRODUCT_NOT_AVAILABLE", openedAt.plusSeconds(2)));
    }

    @Test
    void rejectsIdempotentlyAndNeverOpensAfterRejection() {
        PurchaseOrder order = preparedOrder();
        Instant rejectedAt = CREATED_AT.plusSeconds(1);

        order.reject(" PRODUCT_NOT_AVAILABLE ", rejectedAt);
        order.reject("PRODUCT_NOT_AVAILABLE", rejectedAt.plusSeconds(1));

        assertEquals(PurchaseOrderStatus.REJECTED, order.status());
        assertEquals("PRODUCT_NOT_AVAILABLE", order.supplierRejectionCode());
        assertNull(order.openedAt());
        assertThrows(PurchaseOrderTransitionException.class,
                () -> order.open("SUP-123", rejectedAt.plusSeconds(2)));
    }

    @Test
    void rejectsInvalidLinesHashAndStateReconstitution() {
        PurchaseOrderLine line = line();

        assertThrows(IllegalArgumentException.class,
                () -> PurchaseOrder.prepare(UUID.randomUUID(), "invalid", List.of(line), Set.of(), CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseOrder.prepare(
                        UUID.randomUUID(), PAYLOAD_HASH, List.of(line, line), Set.of(), CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseOrder.reconstitute(
                        UUID.randomUUID(), UUID.randomUUID(), PAYLOAD_HASH, PurchaseOrderStatus.OPEN, List.of(line),
                        Set.of(), null, null, CREATED_AT, CREATED_AT, null));
    }

    @Test
    void rejectsInvalidSnapshotAndTransitionTimestamp() {
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrderLine(UUID.randomUUID(), " ", "Oil filter", StockItemType.PART, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PurchaseOrderLine(UUID.randomUUID(), "SKU", "Oil filter", StockItemType.PART, 0));

        PurchaseOrder order = preparedOrder();
        assertThrows(IllegalArgumentException.class,
                () -> order.open("SUP-123", CREATED_AT.minusSeconds(1)));
    }

    private static PurchaseOrder preparedOrder() {
        return PurchaseOrder.prepare(UUID.randomUUID(), PAYLOAD_HASH, List.of(line()), Set.of(), CREATED_AT);
    }

    private static PurchaseOrderLine line() {
        return new PurchaseOrderLine(
                UUID.randomUUID(), " oil-filter-001 ", " Oil filter ", StockItemType.PART, 5);
    }
}
