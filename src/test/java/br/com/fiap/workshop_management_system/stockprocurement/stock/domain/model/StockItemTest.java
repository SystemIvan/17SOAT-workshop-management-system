package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StockItemTest {
    private StockItem item(StockItemType type, int quantity) {
        return StockItem.create(new Sku(" oil-001 "), "Oil filter", type,
                new Price(new BigDecimal("45.90"), CurrencyCode.BRL), new Quantity(quantity));
    }

    @Test
    void createsAllSupportedTypesWithNormalizedSku() {
        for (StockItemType type : StockItemType.values()) {
            StockItem item = item(type, 1);
            assertEquals("OIL-001", item.sku().value());
            assertEquals(type, item.type());
            assertTrue(item.active());
        }
    }

    @Test
    void rejectsInvalidValueObjectsAndName() {
        assertThrows(IllegalArgumentException.class, () -> new Sku(" "));
        assertThrows(IllegalArgumentException.class, () -> new Sku("a".repeat(101)));
        assertThrows(IllegalArgumentException.class, () -> new Quantity(-1));
        assertThrows(IllegalArgumentException.class,
                () -> new Price(new BigDecimal("1.001"), CurrencyCode.BRL));
        assertThrows(IllegalArgumentException.class,
                () -> new Price(new BigDecimal("-1.00"), CurrencyCode.BRL));
        assertThrows(IllegalArgumentException.class,
                () -> StockItem.create(new Sku("SKU"), " ", StockItemType.PART,
                        new Price(BigDecimal.ZERO, CurrencyCode.BRL), new Quantity(0)));
    }

    @Test
    void permitsZeroPriceAndQuantityAndIdentifiesAvailability() {
        StockItem item = StockItem.create(new Sku("SKU"), "Supply", StockItemType.SUPPLY,
                new Price(BigDecimal.ZERO, CurrencyCode.BRL), new Quantity(0));
        assertEquals(new BigDecimal("0.00"), item.price().value());
        assertFalse(item.hasAvailableQuantity());
    }

    @Test
    void updatesOnlyActiveItemAndDeactivationIsIdempotent() {
        StockItem item = item(StockItemType.CONSUMABLE, 3);
        item.updateDetails("Premium oil filter", new Price(new BigDecimal("50.00"), CurrencyCode.BRL));
        assertEquals("Premium oil filter", item.name());
        item.deactivate();
        item.deactivate();
        assertFalse(item.active());
        assertThrows(StockItemInactiveException.class,
                () -> item.updateDetails("Other", new Price(BigDecimal.ONE, CurrencyCode.BRL)));
    }

    @Test
    void reconstitutesThePersistedState() {
        UUID id = UUID.randomUUID();
        StockItem item = StockItem.reconstitute(id, new Sku("SKU"), "Inactive", StockItemType.PART,
                new Price(BigDecimal.TEN, CurrencyCode.BRL), new Quantity(2), false);
        assertEquals(id, item.id());
        assertFalse(item.active());
        assertTrue(item.hasAvailableQuantity());
    }

    @Test
    void assessesAndReservesAvailableQuantityWithoutAllowingNegativeBalance() {
        StockItem item = item(StockItemType.PART, 2);

        StockItemReservationAssessment assessment = item.assessReservation(new Quantity(2));

        assertTrue(assessment.eligible());
        assertEquals(new Quantity(2), item.availableQuantity());
        item.reserve(new Quantity(2));
        assertEquals(new Quantity(0), item.availableQuantity());
        assertEquals(StockItemReservationEligibility.INSUFFICIENT_QUANTITY,
                item.assessReservation(new Quantity(1)).eligibility());
        assertThrows(IllegalStateException.class, () -> item.reserve(new Quantity(1)));
    }

    @Test
    void distinguishesInactiveItemsAndRejectsNonPositiveReservations() {
        StockItem item = item(StockItemType.PART, 2);
        item.deactivate();

        assertEquals(StockItemReservationEligibility.INACTIVE,
                item.assessReservation(new Quantity(1)).eligibility());
        assertThrows(IllegalStateException.class, () -> item.reserve(new Quantity(1)));
        assertThrows(IllegalArgumentException.class, () -> item.assessReservation(new Quantity(0)));
    }

    @Test
    void receivesIntoActiveOrInactiveItemsWithoutChangingActivation() {
        StockItem item = item(StockItemType.PART, 2);
        item.deactivate();

        StockItemReceiptBalance balance = item.receive(new Quantity(3));

        assertEquals(new Quantity(2), balance.availableBefore());
        assertEquals(new Quantity(5), balance.availableAfter());
        assertEquals(new Quantity(5), item.availableQuantity());
        assertFalse(item.active());
    }

    @Test
    void rejectsInvalidOrOverflowingReceipts() {
        StockItem item = item(StockItemType.PART, Integer.MAX_VALUE);

        assertThrows(IllegalArgumentException.class, () -> item.receive(new Quantity(0)));
        assertThrows(ArithmeticException.class, () -> item.receive(new Quantity(1)));
        assertEquals(new Quantity(Integer.MAX_VALUE), item.availableQuantity());
    }
}
