package br.com.fiap.workshop_management_system.registration.customer.infrastructure.bootstrap;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
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

    private static final TaxId TAX_ID = new TaxId("11144477735");

    private final CustomerRepository customerRepository;

    CustomerDevelopmentDataSeeder(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!customerRepository.existsByTaxId(TAX_ID)) {
            Customer customer = Customer.create(
                    "Cliente de Desenvolvimento",
                    TAX_ID,
                    new ContactInfo("customer@example.test", "+5500000000000"));
            customerRepository.save(customer);
        }
    }
}
