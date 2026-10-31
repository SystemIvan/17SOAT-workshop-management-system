package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerEmailTest {

    @Test
    void exposesTheRegisteredEmail() {
        Customer customer = Customer.create(
                "Jane Doe", new TaxId("52998224725"), new ContactInfo("jane.doe@example.com", "11999999999"));

        CustomerEmail email = CustomerEmail.of(customer);

        assertTrue(email.isPresent());
        assertEquals("jane.doe@example.com", email.value());
    }

    @Test
    void isAbsentWhenTheCustomerHasNoContactInfo() {
        // ContactInfo currently rejects a missing e-mail, so the "no e-mail" case can only be reached with a mock.
        Customer customer = mock(Customer.class);
        when(customer.contactInfo()).thenReturn(null);

        assertFalse(CustomerEmail.of(customer).isPresent());
    }

    @Test
    void isAbsentWhenTheEmailIsBlank() {
        assertFalse(new CustomerEmail(" ").isPresent());
        assertFalse(new CustomerEmail(null).isPresent());
    }
}
