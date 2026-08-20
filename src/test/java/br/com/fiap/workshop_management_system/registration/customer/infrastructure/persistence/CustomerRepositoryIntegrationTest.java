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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("Primeiro Cliente", repository.findActiveByTaxId(taxId).orElseThrow().name());
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
        assertTrue(updated.active());
    }

    @Test
    void persistsArchiveWithoutDeletingAndExcludesItFromActiveQueries() {
        Customer customer = customer("Cliente Arquivado", new TaxId("11144477735"));
        repository.save(customer);

        customer.archive();
        repository.save(customer);

        Customer historicalCustomer = repository.findById(customer.id()).orElseThrow();
        assertFalse(historicalCustomer.active());
        assertTrue(repository.findActiveByTaxId(customer.taxId()).isEmpty());
        assertTrue(repository.findAllActive().stream().noneMatch(found -> found.id().equals(customer.id())));
        assertTrue(repository.existsByTaxId(customer.taxId()));
    }

    private static Customer customer(String name, TaxId taxId) {
        return Customer.create(name, taxId, new ContactInfo("customer@example.test", "+5511999999999"));
    }
}
