package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.ContactInfoDTO;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CreateCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
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

class CustomerTaxIdUseCaseTest {

    @Test
    void createsWithNormalizedTaxIdAndIdentifiesFormattedValue() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CreateCustomerUseCase create = new CreateCustomerUseCase(repository);

        var created = create.execute(request("529.982.247-25"));
        var identified = new IdentifyCustomerByTaxIdUseCase(repository).execute("52998224725");

        assertEquals("52998224725", created.document());
        assertEquals(created.id(), identified.id());
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void createsAndIdentifiesCnpjInEitherRepresentation() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CreateCustomerUseCase create = new CreateCustomerUseCase(repository);

        var created = create.execute(request("11.222.333/0001-81"));
        var identified = new IdentifyCustomerByTaxIdUseCase(repository).execute("11222333000181");

        assertEquals("11222333000181", created.document());
        assertEquals(created.id(), identified.id());
    }

    @Test
    void rejectsNormalizedDuplicateWithoutAnotherSave() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CreateCustomerUseCase create = new CreateCustomerUseCase(repository);
        create.execute(request("52998224725"));

        assertThrows(CustomerTaxIdAlreadyExistsException.class,
                () -> create.execute(request("529.982.247-25")));
        assertEquals(1, repository.saveCalls);
    }

    @Test
    void rejectsInvalidTaxIdBeforeSave() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();

        assertThrows(IllegalArgumentException.class,
                () -> new CreateCustomerUseCase(repository).execute(request("52998224724")));
        assertEquals(0, repository.saveCalls);
    }

    @Test
    void reportsCustomerNotFoundWithoutDisclosingTaxId() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();

        CustomerNotFoundException exception = assertThrows(CustomerNotFoundException.class,
                () -> new IdentifyCustomerByTaxIdUseCase(repository).execute("11222333000181"));

        assertEquals("Cliente não encontrado", exception.getMessage());
    }

    private static CreateCustomerRequest request(String document) {
        return new CreateCustomerRequest("Maria Souza", document,
                new ContactInfoDTO("maria@example.test", "+5511999999999"));
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
            customers.add(customer);
        }
    }
}
