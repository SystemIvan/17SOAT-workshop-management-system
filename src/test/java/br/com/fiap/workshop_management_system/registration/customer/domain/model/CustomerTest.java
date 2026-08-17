package br.com.fiap.workshop_management_system.registration.customer.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerTest {

    private final ContactInfo contactInfo = new ContactInfo("cliente@example.com", "(11) 99999-8888");

    private Customer newCustomer() {
        return Customer.create("Maria Souza", new TaxId("529.982.247-25"), contactInfo);
    }

    @Test
    void createdCustomerHasProvidedData() {
        Customer customer = newCustomer();

        assertEquals("Maria Souza", customer.name());
        assertEquals("52998224725", customer.taxId().value());
        assertEquals(contactInfo, customer.contactInfo());
    }

    @Test
    void cannotCreateCustomerWithBlankName() {
        assertThrows(IllegalArgumentException.class,
                () -> Customer.create(" ", new TaxId("52998224725"), contactInfo));
    }

    @Test
    void cannotCreateCustomerWithoutTaxId() {
        assertThrows(IllegalArgumentException.class, () -> Customer.create("Maria Souza", null, contactInfo));
    }

    @Test
    void cannotCreateCustomerWithoutContactInfo() {
        assertThrows(IllegalArgumentException.class,
                () -> Customer.create("Maria Souza", new TaxId("52998224725"), null));
    }

    @Test
    void contactInfoRejectsInvalidEmailOrBlankPhone() {
        assertThrows(IllegalArgumentException.class, () -> new ContactInfo("invalid-email", "11999998888"));
        assertThrows(IllegalArgumentException.class, () -> new ContactInfo("cliente@example.com", " "));
    }

    @Test
    void updateContactInfoReplacesTheCurrentContact() {
        Customer customer = newCustomer();
        TaxId originalTaxId = customer.taxId();
        ContactInfo newContactInfo = new ContactInfo("novo@example.com", "11888887777");

        customer.updateContactInfo(newContactInfo.email(), newContactInfo.phone(), null);

        assertEquals(newContactInfo, customer.contactInfo());
        assertEquals(originalTaxId, customer.taxId());
    }

    @Test
    void updateContactInfoRejectsNull() {
        Customer customer = newCustomer();

        assertThrows(IllegalArgumentException.class, () -> customer.updateContactInfo(null, null, null));
    }

    @Test
    void renameUpdatesNameButRejectsBlank() {
        Customer customer = newCustomer();
        TaxId originalTaxId = customer.taxId();

        customer.rename("Maria Oliveira");
        assertEquals("Maria Oliveira", customer.name());
        assertEquals(originalTaxId, customer.taxId());
        assertThrows(IllegalArgumentException.class, () -> customer.rename(""));
    }

    @Test
    void reconstituteRestoresExactPersistedState() {
        UUID id = UUID.randomUUID();

        Customer customer = Customer.reconstitute(id, "Joao Pedro", new TaxId("11.222.333/0001-81"), contactInfo);

        assertEquals(id, customer.id());
        assertEquals("Joao Pedro", customer.name());
        assertEquals("11222333000181", customer.taxId().value());
        assertEquals(contactInfo, customer.contactInfo());
    }
}
