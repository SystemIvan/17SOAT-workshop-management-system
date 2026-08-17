package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerMapperTest {

    @Test
    void mapsActiveLifecycleStateToResponse() {
        Customer customer = Customer.create(
                "Maria Souza", new TaxId("52998224725"), new ContactInfo("cliente@example.com", "11999998888"));

        CustomerResponse response = CustomerMapper.toResponse(customer);

        assertTrue(response.active());
    }

    @Test
    void mapsArchivedLifecycleStateToResponse() {
        Customer customer = Customer.reconstitute(
                UUID.randomUUID(),
                "Maria Souza",
                new TaxId("52998224725"),
                new ContactInfo("cliente@example.com", "11999998888"),
                false);

        CustomerResponse response = CustomerMapper.toResponse(customer);

        assertFalse(response.active());
    }
}
