package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleYearTest {

    @Test
    void acceptsInclusiveYearBoundaries() {
        assertEquals(1886, VehicleYear.create(1886, 2026).value());
        assertEquals(2027, VehicleYear.create(2027, 2026).value());
    }

    @Test
    void rejectsYearsOutsideBoundaries() {
        assertThrows(IllegalArgumentException.class, () -> VehicleYear.create(1885, 2026));
        assertThrows(IllegalArgumentException.class, () -> VehicleYear.create(2028, 2026));
    }

    @Test
    void reconstitutesPersistedValidYear() {
        assertEquals(2027, VehicleYear.reconstitute(2027).value());
        assertThrows(IllegalArgumentException.class, () -> VehicleYear.reconstitute(1885));
    }
}
