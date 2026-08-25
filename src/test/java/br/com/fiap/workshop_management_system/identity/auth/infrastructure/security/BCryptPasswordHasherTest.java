package br.com.fiap.workshop_management_system.identity.auth.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashNeverEqualsTheRawPassword() {
        String hash = hasher.hash("correct-password");

        assertNotEquals("correct-password", hash);
    }

    @Test
    void matchesReturnsTrueForTheCorrectPassword() {
        String hash = hasher.hash("correct-password");

        assertTrue(hasher.matches("correct-password", hash));
    }

    @Test
    void matchesReturnsFalseForAWrongPassword() {
        String hash = hasher.hash("correct-password");

        assertFalse(hasher.matches("wrong-password", hash));
    }
}
