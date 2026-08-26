package br.com.fiap.workshop_management_system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class CiQualityGateValidationTest {

    @Test
    void shouldFailOnlyToValidateTheRemoteQualityGate() {
        fail("Intentional failure for the temporary CI quality gate validation branch");
    }
}
