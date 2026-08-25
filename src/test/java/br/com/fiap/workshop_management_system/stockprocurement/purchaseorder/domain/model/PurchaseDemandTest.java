package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PurchaseDemandTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-24T15:30:00.123456789Z");

    @Test
    void createsAndUpdatesAnOpenPendingRepairDemand() {
        PurchaseDemand demand = PurchaseDemand.createPendingRepair(
                UUID.randomUUID(), UUID.randomUUID(), 5, 2, 3, CREATED_AT);
        Instant updateTime = Instant.parse("2026-08-24T15:31:00.987654321Z");

        demand.recordObservation(7, 1, 6, updateTime);

        assertEquals(PurchaseDemandOrigin.PENDING_REPAIR, demand.origin());
        assertEquals(PurchaseDemandStatus.OPEN, demand.status());
        assertEquals(7, demand.requestedQuantity());
        assertEquals(1, demand.observedAvailableQuantity());
        assertEquals(6, demand.suggestedQuantity());
        assertEquals(Instant.parse("2026-08-24T15:30:00.123456Z"), demand.createdAt());
        assertEquals(Instant.parse("2026-08-24T15:31:00.987654Z"), demand.updatedAt());
    }

    @Test
    void enforcesQuantityRulesForEachOrigin() {
        UUID referenceId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> PurchaseDemand.createPendingRepair(referenceId, stockItemId, 0, 0, 1, CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseDemand.createLowStock(referenceId, stockItemId, -1, 1, CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> PurchaseDemand.reconstitute(
                        UUID.randomUUID(), PurchaseDemandOrigin.LOW_STOCK, referenceId, stockItemId, 1, 0, 1,
                        PurchaseDemandStatus.OPEN, null, CREATED_AT, CREATED_AT, null));
    }

    @Test
    void claimsAndOrdersOnlyForTheClaimingPurchaseOrder() {
        PurchaseDemand demand = pendingRepairDemand();
        UUID purchaseOrderId = UUID.randomUUID();
        Instant claimTime = CREATED_AT.plusSeconds(1);

        demand.claim(purchaseOrderId, claimTime);
        demand.claim(purchaseOrderId, claimTime.plusSeconds(1));

        assertEquals(PurchaseDemandStatus.CLAIMED, demand.status());
        assertEquals(purchaseOrderId, demand.claimedByPurchaseOrderId());
        assertThrows(PurchaseDemandNotSelectableException.class,
                () -> demand.markOrdered(UUID.randomUUID(), claimTime.plusSeconds(2)));

        demand.markOrdered(purchaseOrderId, claimTime.plusSeconds(2));
        demand.markOrdered(purchaseOrderId, claimTime.plusSeconds(3));

        assertEquals(PurchaseDemandStatus.ORDERED, demand.status());
        assertNull(demand.claimedByPurchaseOrderId());
    }

    @Test
    void releasesAClaimAfterSupplierRejection() {
        PurchaseDemand demand = pendingRepairDemand();
        UUID purchaseOrderId = UUID.randomUUID();

        demand.claim(purchaseOrderId, CREATED_AT.plusSeconds(1));
        demand.release(purchaseOrderId, CREATED_AT.plusSeconds(2));
        demand.release(purchaseOrderId, CREATED_AT.plusSeconds(3));

        assertEquals(PurchaseDemandStatus.OPEN, demand.status());
        assertNull(demand.claimedByPurchaseOrderId());
    }

    @Test
    void resolvesOnlyOpenDemandAndIgnoresLaterObservations() {
        PurchaseDemand demand = pendingRepairDemand();
        Instant resolutionTime = CREATED_AT.plusSeconds(1);

        demand.resolve(resolutionTime);
        demand.recordObservation(10, 0, 10, resolutionTime.plusSeconds(1));

        assertEquals(PurchaseDemandStatus.RESOLVED, demand.status());
        assertEquals(resolutionTime.truncatedTo(java.time.temporal.ChronoUnit.MICROS), demand.resolvedAt());
        assertEquals(5, demand.requestedQuantity());
    }

    @Test
    void ignoresResolutionOnceSubmissionHasClaimedTheDemand() {
        PurchaseDemand demand = pendingRepairDemand();
        UUID purchaseOrderId = UUID.randomUUID();

        demand.claim(purchaseOrderId, CREATED_AT.plusSeconds(1));
        demand.resolve(CREATED_AT.plusSeconds(2));

        assertEquals(PurchaseDemandStatus.CLAIMED, demand.status());
        assertNull(demand.resolvedAt());
    }

    @Test
    void rejectsInconsistentReconstitutionAndPastTransitions() {
        UUID referenceId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class,
                () -> PurchaseDemand.reconstitute(
                        UUID.randomUUID(), PurchaseDemandOrigin.PENDING_REPAIR, referenceId, stockItemId, 5, 2, 3,
                        PurchaseDemandStatus.CLAIMED, null, CREATED_AT, CREATED_AT, null));

        PurchaseDemand demand = pendingRepairDemand();
        assertThrows(IllegalArgumentException.class,
                () -> demand.claim(UUID.randomUUID(), CREATED_AT.minusSeconds(1)));
    }

    private static PurchaseDemand pendingRepairDemand() {
        return PurchaseDemand.createPendingRepair(
                UUID.randomUUID(), UUID.randomUUID(), 5, 2, 3, CREATED_AT);
    }
}
