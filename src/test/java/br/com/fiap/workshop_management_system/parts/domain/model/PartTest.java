package br.com.fiap.workshop_management_system.parts.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PartTest {

    private final Price price = new Price(BigDecimal.TEN);

    private Part newPart(int initialQuantity) {
        return Part.create("Pastilha de freio", "SKU-001", initialQuantity, price);
    }

    @Test
    void createdPartHasProvidedData() {
        Part part = newPart(10);

        assertEquals("Pastilha de freio", part.name());
        assertEquals("SKU-001", part.sku());
        assertEquals(10, part.quantity().value());
        assertEquals(price, part.price());
    }

    @Test
    void cannotCreatePartWithBlankNameOrSku() {
        assertThrows(IllegalArgumentException.class, () -> Part.create(" ", "SKU-001", 10, price));
        assertThrows(IllegalArgumentException.class, () -> Part.create("Pastilha de freio", " ", 10, price));
    }

    @Test
    void cannotCreatePartWithoutPrice() {
        assertThrows(IllegalArgumentException.class, () -> Part.create("Pastilha de freio", "SKU-001", 10, null));
    }

    @Test
    void quantityCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Quantity(-1));
    }

    @Test
    void priceCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Price(BigDecimal.valueOf(-1)));
    }

    @Test
    void increaseStockAddsToCurrentQuantity() {
        Part part = newPart(5);

        part.increaseStock(3);

        assertEquals(8, part.quantity().value());
    }

    @Test
    void decreaseStockSubtractsFromCurrentQuantity() {
        Part part = newPart(5);

        part.decreaseStock(3);

        assertEquals(2, part.quantity().value());
    }

    @Test
    void cannotDecreaseStockBelowZero() {
        Part part = newPart(2);

        assertThrows(IllegalStateException.class, () -> part.decreaseStock(3));
    }

    @Test
    void stockAdjustmentAmountsMustBePositive() {
        Part part = newPart(5);

        assertThrows(IllegalArgumentException.class, () -> part.increaseStock(0));
        assertThrows(IllegalArgumentException.class, () -> part.decreaseStock(-1));
    }

    @Test
    void changePriceReplacesTheCurrentPrice() {
        Part part = newPart(5);
        Price newPrice = new Price(BigDecimal.valueOf(25));

        part.changePrice(newPrice);

        assertEquals(newPrice, part.price());
    }

    @Test
    void changePriceRejectsNull() {
        Part part = newPart(5);

        assertThrows(IllegalArgumentException.class, () -> part.changePrice(null));
    }

    @Test
    void renameUpdatesNameButRejectsBlank() {
        Part part = newPart(5);

        part.rename("Pastilha dianteira");
        assertEquals("Pastilha dianteira", part.name());
        assertThrows(IllegalArgumentException.class, () -> part.rename(""));
    }

    @Test
    void reconstituteRestoresExactPersistedState() {
        UUID id = UUID.randomUUID();
        Quantity quantity = new Quantity(7);

        Part part = Part.reconstitute(id, "Filtro de oleo", "SKU-002", quantity, price);

        assertEquals(id, part.id());
        assertEquals("Filtro de oleo", part.name());
        assertEquals("SKU-002", part.sku());
        assertEquals(quantity, part.quantity());
        assertEquals(price, part.price());
    }
}
