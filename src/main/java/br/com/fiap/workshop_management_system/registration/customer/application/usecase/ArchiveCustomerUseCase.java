package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ArchiveCustomerUseCase {

    private final CustomerRepository repository;

    public ArchiveCustomerUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(UUID id) {
        Customer customer = CustomerFinder.getOrThrow(repository, id);
        customer.archive();
        repository.save(customer);
    }
}
