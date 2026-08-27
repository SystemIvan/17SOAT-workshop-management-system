package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowStockPolicyTest {

    @Test
    void validatesQuantitiesAndTheirRequiredRelation() {
        assertThrows(InvalidLowStockPolicyException.class, () -> new LowStockPolicy(null, new Quantity(1)));
        assertThrows(InvalidLowStockPolicyException.class, () -> new LowStockPolicy(new Quantity(1), null));
        assertThrows(InvalidLowStockPolicyException.class,
                () -> new LowStockPolicy(new Quantity(3), new Quantity(3)));
        assertThrows(InvalidLowStockPolicyException.class,
                () -> new LowStockPolicy(new Quantity(4), new Quantity(3)));
    }

    @Test
    void usesStrictComparisonAndCalculatesPositiveSuggestion() {
        LowStockPolicy policy = new LowStockPolicy(new Quantity(5), new Quantity(12));

        assertTrue(policy.isLow(new Quantity(4)));
        assertFalse(policy.isLow(new Quantity(5)));
        assertEquals(new Quantity(8), policy.suggestedPurchase(new Quantity(4)));
        assertThrows(InvalidLowStockPolicyException.class, () -> policy.suggestedPurchase(new Quantity(5)));
    }
}
