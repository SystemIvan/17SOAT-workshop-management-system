package br.com.fiap.workshop_management_system.registration.customer.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomerDevelopmentDataSeederTest {

    @Test
    void seedOnlyOnceWhenRunRepeatedly() {
        InMemoryCustomerRepository repository = new InMemoryCustomerRepository();
        CustomerDevelopmentDataSeeder seeder = new CustomerDevelopmentDataSeeder(repository);

        seeder.run(null);
        seeder.run(null);

        assertEquals(1, repository.customers.size());
        assertEquals("11144477735", repository.customers.getFirst().taxId().value());
    }

    private static final class InMemoryCustomerRepository implements CustomerRepository {

        private final List<Customer> customers = new ArrayList<>();

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
            customers.add(customer);
        }
    }
}
