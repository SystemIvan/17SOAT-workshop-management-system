package br.com.fiap.workshop_management_system.identity.auth.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsernameTest {

    @Test
    void stripsSurroundingWhitespace() {
        Username username = new Username("  jane.doe  ");

        assertEquals("jane.doe", username.value());
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Username(null));
    }

    @Test
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new Username("   "));
    }

    @Test
    void rejectsValueLongerThanMaxLength() {
        String tooLong = "a".repeat(256);

        assertThrows(IllegalArgumentException.class, () -> new Username(tooLong));
    }
}
