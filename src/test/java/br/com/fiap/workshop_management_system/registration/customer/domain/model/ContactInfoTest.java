package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContactInfoTest {

    @Test
    void normalizesValidEmailPhoneAndAddressValues() {
        Email email = new Email("  cliente@example.test  ");
        Phone brazilianPhone = new Phone("(11) 99999-8888");
        Phone internationalPhone = new Phone("+351 912 345 678");
        Address address = address("  Avenida Paulista  ", "sp", "01310-100");

        assertEquals("cliente@example.test", email.value());
        assertEquals("+5511999998888", brazilianPhone.value());
        assertEquals("+351912345678", internationalPhone.value());
        assertEquals("Avenida Paulista", address.street());
        assertEquals("SP", address.state());
        assertEquals("01310100", address.postalCode());
        assertNull(address.complement());
        assertNull(address.neighborhood());
    }

    @Test
    void rejectsInvalidEmailAndPhoneValues() {
        assertThrows(IllegalArgumentException.class, () -> new Email("cliente@invalid"));
        assertThrows(IllegalArgumentException.class, () -> new Email("cliente @example.test"));
        assertThrows(IllegalArgumentException.class, () -> new Phone("123456789"));
        assertThrows(IllegalArgumentException.class, () -> new Phone("5511999998888"));
        assertThrows(IllegalArgumentException.class, () -> new Phone("+05511999998888"));
        assertThrows(IllegalArgumentException.class, () -> new Phone("+55/11/99999-8888"));
    }

    @Test
    void rejectsIncompleteOrInvalidAddress() {
        assertThrows(IllegalArgumentException.class, () -> address(" ", "SP", "01310-100"));
        assertThrows(IllegalArgumentException.class, () -> address("Avenida Paulista", "XX", "01310-100"));
        assertThrows(IllegalArgumentException.class, () -> address("Avenida Paulista", "SP", "0131-0100"));
    }

    @Test
    void appliesOnlyProvidedContactUpdatesAndRejectsEmptyUpdate() {
        Address originalAddress = address("Avenida Paulista", "SP", "01310-100");
        ContactInfo original = new ContactInfo("cliente@example.test", "11999998888", originalAddress);

        ContactInfo emailChanged = original.withUpdates(new Email("novo@example.test"), null, null);
        ContactInfo addressChanged = emailChanged.withUpdates(null, null,
                address("Rua da Consolação", "SP", "01301-000"));

        assertEquals("novo@example.test", emailChanged.email().value());
        assertEquals(original.phone(), emailChanged.phone());
        assertEquals(originalAddress, emailChanged.address());
        assertEquals(emailChanged.email(), addressChanged.email());
        assertEquals("Rua da Consolação", addressChanged.address().street());
        assertThrows(IllegalArgumentException.class, () -> original.withUpdates(null, null, null));
    }

    private static Address address(String street, String state, String postalCode) {
        return new Address(street, "1000", " ", null, "São Paulo", state, postalCode);
    }
}
