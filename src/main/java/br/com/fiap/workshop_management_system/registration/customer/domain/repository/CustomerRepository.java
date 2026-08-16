package br.com.fiap.workshop_management_system.registration.customer.domain.repository;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Porta do agregado Customer. Cada raiz de agregado possui seu próprio repositório;
 * a consistência transacional fica restrita à fronteira do agregado.
 */
public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByTaxId(TaxId taxId);

    boolean existsByTaxId(TaxId taxId);

    List<Customer> findAll();

    void save(Customer customer);
}
