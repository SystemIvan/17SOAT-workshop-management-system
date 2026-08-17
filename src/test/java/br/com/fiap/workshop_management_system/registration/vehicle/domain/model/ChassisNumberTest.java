package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChassisNumberTest {

    @Test
    void normalizesValidChassis() {
        assertEquals("9BWZZZ377VT004251", new ChassisNumber(" 9bwzzz377vt004251 ").value());
    }

    @Test
    void rejectsNullBlankWrongLengthAndSeparators() {
        assertThrows(IllegalArgumentException.class, () -> new ChassisNumber(null));
        assertThrows(IllegalArgumentException.class, () -> new ChassisNumber(""));
        assertThrows(IllegalArgumentException.class, () -> new ChassisNumber("9BWZZZ377VT00425"));
        assertThrows(IllegalArgumentException.class, () -> new ChassisNumber("9BWZZZ377VT0042510"));
        assertThrows(IllegalArgumentException.class, () -> new ChassisNumber("9BWZZZ377VT-04251"));
    }
}
