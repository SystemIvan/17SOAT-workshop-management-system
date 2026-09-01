package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MileageTest {

    @Test
    void acceptsZeroAndLongMaximumValue() {
        assertEquals(0, new Mileage(0).value());
        assertEquals(Long.MAX_VALUE, new Mileage(Long.MAX_VALUE).value());
    }

    @Test
    void rejectsNegativeValue() {
        assertThrows(IllegalArgumentException.class, () -> new Mileage(-1));
    }

    @Test
    void comparesMileageValues() {
        assertEquals(-1, Integer.signum(new Mileage(10).compareTo(new Mileage(11))));
        assertEquals(0, new Mileage(11).compareTo(new Mileage(11)));
        assertEquals(1, Integer.signum(new Mileage(12).compareTo(new Mileage(11))));
    }
}
