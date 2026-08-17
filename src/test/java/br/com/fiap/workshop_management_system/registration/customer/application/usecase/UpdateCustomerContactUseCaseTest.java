package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.AddressDTO;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateContactInfoDTO;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateCustomerContactRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Address;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateCustomerContactUseCaseTest {

    @Test
    void updatesOnlyProvidedFieldsAndPreservesCustomerIdentity() {
        InMemoryCustomerRepository repository = repositoryWithCustomer();
        Customer original = repository.customers.getFirst();
        UpdateCustomerContactUseCase useCase = new UpdateCustomerContactUseCase(repository);

        var response = useCase.execute(original.id(), request(
                Optional.of("novo@example.test"), Optional.empty(), Optional.empty()));

        assertEquals(original.id(), response.id());
        assertEquals(original.name(), response.name());
        assertEquals(original.taxId().value(), response.document());
        assertEquals("novo@example.test", response.contactInfo().email());
        assertEquals("+5511999998888", response.contactInfo().phone());
        assertEquals("Avenida Paulista", response.contactInfo().address().street());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void updatesPhoneAndAddressIndividuallyAndAcceptsIdempotentValues() {
        InMemoryCustomerRepository repository = repositoryWithCustomer();
        Customer customer = repository.customers.getFirst();
        AddressDTO newAddress = new AddressDTO("Rua Augusta", "500", "Apto 12", "Consolação",
                "São Paulo", "sp", "01305-000");

        UpdateCustomerContactUseCase useCase = new UpdateCustomerContactUseCase(repository);
        var phoneResponse = useCase.execute(customer.id(), request(
                Optional.empty(), Optional.of("(11) 98888-7777"), Optional.empty()));
        var addressResponse = useCase.execute(customer.id(), request(
                Optional.empty(), Optional.empty(), Optional.of(newAddress)));
        var idempotentResponse = useCase.execute(customer.id(), request(
                Optional.empty(), Optional.empty(), Optional.of(newAddress)));

        assertEquals("+5511988887777", phoneResponse.contactInfo().phone());
        assertEquals("Avenida Paulista", phoneResponse.contactInfo().address().street());
        assertEquals("cliente@example.test", addressResponse.contactInfo().email());
        assertEquals("+5511988887777", addressResponse.contactInfo().phone());
        assertEquals("Rua Augusta", addressResponse.contactInfo().address().street());
        assertEquals("SP", addressResponse.contactInfo().address().state());
        assertEquals("01305000", addressResponse.contactInfo().address().postalCode());
        assertEquals(addressResponse, idempotentResponse);
        assertEquals(3, repository.saveCalls);
    }

    @Test
    void rejectsInvalidOrEmptyUpdateWithoutSaving() {
        InMemoryCustomerRepository repository = repositoryWithCustomer();
        Customer customer = repository.customers.getFirst();
        UpdateCustomerContactUseCase useCase = new UpdateCustomerContactUseCase(repository);

        assertThrows(IllegalArgumentException.class, () -> useCase.execute(customer.id(), request(
                Optional.of("invalid-email"), Optional.empty(), Optional.empty())));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(customer.id(), request(
                Optional.empty(), Optional.empty(), Optional.empty())));
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void reportsCustomerNotFound() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();

        assertThrows(CustomerNotFoundException.class, () -> new UpdateCustomerContactUseCase(repository).execute(
                UUID.randomUUID(), request(Optional.of("novo@example.test"), Optional.empty(), Optional.empty())));
        assertEquals(0, repository.saveCalls);
    }

    private static UpdateCustomerContactRequest request(
            Optional<String> email,
            Optional<String> phone,
            Optional<AddressDTO> address) {
        return new UpdateCustomerContactRequest(new UpdateContactInfoDTO(email, phone, address));
    }

    private static InMemoryCustomerRepository repositoryWithCustomer() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        Address address = new Address("Avenida Paulista", "1000", null, "Bela Vista", "São Paulo", "SP",
                "01310-100");
        repository.customers.add(Customer.create("Maria Souza", new TaxId("52998224725"),
                new ContactInfo("cliente@example.test", "(11) 99999-8888", address)));
        return repository;
    }

    private static final class InMemoryCustomerRepository implements CustomerRepository {

        private final List<Customer> customers = new ArrayList<>();
        private int saveCalls;

        @Override
        public Optional<Customer> findById(UUID id) {
            return customers.stream().filter(customer -> customer.id().equals(id)).findFirst();
        }

        @Override
        public Optional<Customer> findByIdForUpdate(UUID id) {
            return findById(id);
        }

        @Override
        public Optional<Customer> findActiveByTaxId(TaxId taxId) {
            return customers.stream()
                    .filter(Customer::active)
                    .filter(customer -> customer.taxId().equals(taxId))
                    .findFirst();
        }

        @Override
        public boolean existsByTaxId(TaxId taxId) {
            return customers.stream().anyMatch(customer -> customer.taxId().equals(taxId));
        }

        @Override
        public List<Customer> findAllActive() {
            return customers.stream().filter(Customer::active).toList();
        }

        @Override
        public void save(Customer customer) {
            saveCalls++;
            customers.removeIf(existing -> existing.id().equals(customer.id()));
            customers.add(customer);
        }
    }
}
