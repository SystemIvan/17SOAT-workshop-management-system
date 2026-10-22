package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailMaskingTest {

    @Test
    void keepsOnlyTheFirstCharacterOfLocalPartAndDomain() {
        assertEquals("j***@e***", EmailMasking.mask("jane.doe@example.com"));
    }

    @Test
    void masksEverythingWhenThereIsNoLocalPart() {
        assertEquals("***", EmailMasking.mask("@example.com"));
        assertEquals("***", EmailMasking.mask("no-at-sign"));
    }

    @Test
    void masksAnEmptyDomain() {
        assertEquals("j***@***", EmailMasking.mask("jane@"));
    }

    @Test
    void masksNull() {
        assertEquals("***", EmailMasking.mask(null));
    }
}
