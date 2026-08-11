package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerTest {

    private final ContactInfo contactInfo = new ContactInfo("cliente@example.com", "11999998888");

    private Customer newCustomer() {
        return Customer.create("Maria Souza", "12345678900", contactInfo);
    }

    @Test
    void createdCustomerHasProvidedData() {
        Customer customer = newCustomer();

        assertEquals("Maria Souza", customer.name());
        assertEquals("12345678900", customer.document());
        assertEquals(contactInfo, customer.contactInfo());
    }

    @Test
    void cannotCreateCustomerWithBlankName() {
        assertThrows(IllegalArgumentException.class, () -> Customer.create(" ", "12345678900", contactInfo));
    }

    @Test
    void cannotCreateCustomerWithBlankDocument() {
        assertThrows(IllegalArgumentException.class, () -> Customer.create("Maria Souza", " ", contactInfo));
    }

    @Test
    void cannotCreateCustomerWithoutContactInfo() {
        assertThrows(IllegalArgumentException.class, () -> Customer.create("Maria Souza", "12345678900", null));
    }

    @Test
    void contactInfoRejectsInvalidEmailOrBlankPhone() {
        assertThrows(IllegalArgumentException.class, () -> new ContactInfo("invalid-email", "11999998888"));
        assertThrows(IllegalArgumentException.class, () -> new ContactInfo("cliente@example.com", " "));
    }

    @Test
    void updateContactInfoReplacesTheCurrentContact() {
        Customer customer = newCustomer();
        ContactInfo newContactInfo = new ContactInfo("novo@example.com", "11888887777");

        customer.updateContactInfo(newContactInfo);

        assertEquals(newContactInfo, customer.contactInfo());
    }

    @Test
    void updateContactInfoRejectsNull() {
        Customer customer = newCustomer();

        assertThrows(IllegalArgumentException.class, () -> customer.updateContactInfo(null));
    }

    @Test
    void renameUpdatesNameButRejectsBlank() {
        Customer customer = newCustomer();

        customer.rename("Maria Oliveira");
        assertEquals("Maria Oliveira", customer.name());
        assertThrows(IllegalArgumentException.class, () -> customer.rename(""));
    }

    @Test
    void reconstituteRestoresExactPersistedState() {
        UUID id = UUID.randomUUID();

        Customer customer = Customer.reconstitute(id, "Joao Pedro", "98765432100", contactInfo);

        assertEquals(id, customer.id());
        assertEquals("Joao Pedro", customer.name());
        assertEquals("98765432100", customer.document());
        assertEquals(contactInfo, customer.contactInfo());
    }
}
