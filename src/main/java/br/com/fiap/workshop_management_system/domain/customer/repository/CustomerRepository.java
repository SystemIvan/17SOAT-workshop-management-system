package br.com.fiap.workshop_management_system.domain.customer.repository;

import br.com.fiap.workshop_management_system.domain.customer.model.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for the Customer aggregate. Each Aggregate Root has its own repository;
 * transactional consistency is restricted to the aggregate's own boundary.
 */
public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    List<Customer> findAll();

    void save(Customer customer);
}
