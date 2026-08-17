package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Address;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Email;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CustomerRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository repository;

    @Test
    void databaseUniquenessProtectsNormalizedTaxIdDuringConcurrentLikeSaves() {
        TaxId taxId = new TaxId("11.222.333/0001-81");
        repository.save(customer("Primeiro Cliente", taxId));

        assertThrows(CustomerTaxIdAlreadyExistsException.class,
                () -> repository.save(customer("Segundo Cliente", taxId)));
        assertEquals("Primeiro Cliente", repository.findByTaxId(taxId).orElseThrow().name());
    }

    @Test
    void persistsAndRestoresOptionalAddressAndPartialContactChange() {
        Address address = new Address("Avenida Paulista", "1000", "Conjunto 101", "Bela Vista", "São Paulo",
                "sp", "01310-100");
        Customer customer = Customer.create("Cliente com Endereço", new TaxId("52998224725"),
                new ContactInfo("customer@example.test", "(11) 99999-8888", address));
        repository.save(customer);

        Customer restored = repository.findById(customer.id()).orElseThrow();
        restored.updateContactInfo(new Email("updated@example.test"), null, null);
        repository.save(restored);
        Customer updated = repository.findById(customer.id()).orElseThrow();

        assertEquals("updated@example.test", updated.contactInfo().email().value());
        assertEquals("+5511999998888", updated.contactInfo().phone().value());
        assertEquals(address, updated.contactInfo().address());
    }

    private static Customer customer(String name, TaxId taxId) {
        return Customer.create(name, taxId, new ContactInfo("customer@example.test", "+5511999999999"));
    }
}
