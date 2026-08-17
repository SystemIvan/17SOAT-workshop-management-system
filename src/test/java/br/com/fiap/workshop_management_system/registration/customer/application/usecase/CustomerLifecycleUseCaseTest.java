package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.ContactInfoDTO;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CreateCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.RenameCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateContactInfoDTO;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateCustomerContactRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.CustomerArchivedException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerLifecycleUseCaseTest {

    @Test
    void archivesActiveCustomerAndRepeatedCommandIsIdempotent() {
        InMemoryCustomerRepository repository = repositoryWithCustomer();
        Customer customer = repository.customers.getFirst();
        ArchiveCustomerUseCase useCase = new ArchiveCustomerUseCase(repository);

        useCase.execute(customer.id());
        useCase.execute(customer.id());

        assertFalse(customer.active());
        assertEquals(2, repository.saveCalls);
    }

    @Test
    void archiveReportsNotFoundWithoutSaving() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();

        assertThrows(CustomerNotFoundException.class,
                () -> new ArchiveCustomerUseCase(repository).execute(UUID.randomUUID()));
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void keepsArchivedCustomerHistoricalButRemovesItFromOperationalQueries() {
        InMemoryCustomerRepository repository = repositoryWithCustomer();
        Customer customer = repository.customers.getFirst();
        customer.archive();

        var historical = new GetCustomerUseCase(repository).execute(customer.id());

        assertFalse(historical.active());
        assertTrue(new ListCustomersUseCase(repository).execute().isEmpty());
        assertThrows(CustomerNotFoundException.class,
                () -> new IdentifyCustomerByTaxIdUseCase(repository).execute(customer.taxId().value()));
    }

    @Test
    void keepsArchivedTaxIdReserved() {
        InMemoryCustomerRepository repository = repositoryWithCustomer();
        repository.customers.getFirst().archive();
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Outra Cliente",
                "529.982.247-25",
                new ContactInfoDTO("outra@example.test", "+5511988887777"));

        assertThrows(CustomerTaxIdAlreadyExistsException.class,
                () -> new CreateCustomerUseCase(repository).execute(request));
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void archivedCustomerMutationsFailWithoutSaving() {
        InMemoryCustomerRepository repository = repositoryWithCustomer();
        Customer customer = repository.customers.getFirst();
        customer.archive();
        UpdateCustomerContactRequest updateRequest = new UpdateCustomerContactRequest(
                new UpdateContactInfoDTO(Optional.of("novo@example.test"), Optional.empty(), Optional.empty()));

        assertThrows(CustomerArchivedException.class,
                () -> new RenameCustomerUseCase(repository).execute(
                        customer.id(), new RenameCustomerRequest("Novo Nome")));
        assertThrows(CustomerArchivedException.class,
                () -> new UpdateCustomerContactUseCase(repository).execute(customer.id(), updateRequest));
        assertEquals(0, repository.saveCalls);
    }

    private static InMemoryCustomerRepository repositoryWithCustomer() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        repository.customers.add(Customer.create(
                "Maria Souza",
                new TaxId("52998224725"),
                new ContactInfo("cliente@example.test", "+5511999998888")));
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
