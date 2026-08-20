package br.com.fiap.workshop_management_system.registration.vehicle.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LicensePlateTest {

    @Test
    void normalizesSupportedBrazilianFormats() {
        assertEquals("ABC1234", new LicensePlate(" abc-1234 ").value());
        assertEquals("ABC1234", new LicensePlate("abc1234").value());
        assertEquals("ABC1D23", new LicensePlate(" abc1d23 ").value());
    }

    @Test
    void rejectsUnsupportedFormats() {
        assertThrows(IllegalArgumentException.class, () -> new LicensePlate(null));
        assertThrows(IllegalArgumentException.class, () -> new LicensePlate("ABC-1D23"));
        assertThrows(IllegalArgumentException.class, () -> new LicensePlate("AB C1234"));
        assertThrows(IllegalArgumentException.class, () -> new LicensePlate("ABC123@"));
    }
}
