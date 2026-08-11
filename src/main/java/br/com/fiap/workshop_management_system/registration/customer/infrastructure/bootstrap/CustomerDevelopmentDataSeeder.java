package br.com.fiap.workshop_management_system.registration.customer.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
class CustomerDevelopmentDataSeeder implements ApplicationRunner {

    private static final String DOCUMENT = "00000000000";

    private final CustomerRepository customerRepository;

    CustomerDevelopmentDataSeeder(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean alreadySeeded = customerRepository.findAll().stream()
                .anyMatch(customer -> DOCUMENT.equals(customer.document()));

        if (!alreadySeeded) {
            Customer customer = Customer.create(
                    "Development Customer",
                    DOCUMENT,
                    new ContactInfo("customer@example.test", "+5500000000000"));
            customerRepository.save(customer);
        }
    }
}
