package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;

import java.util.NoSuchElementException;
import java.util.UUID;

final class CustomerFinder {

    private CustomerFinder() {
    }

    static Customer getOrThrow(CustomerRepository repository, UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Customer not found: " + id));
    }
}
