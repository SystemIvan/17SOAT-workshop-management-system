package br.com.fiap.workshop_management_system.infrastructure.customer.persistence;

import br.com.fiap.workshop_management_system.domain.customer.model.Customer;
import br.com.fiap.workshop_management_system.domain.customer.repository.CustomerRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter for the {@link CustomerRepository} port, backed by JPA.
 */
@Repository
public class CustomerRepositoryImpl implements CustomerRepository {

    private final CustomerJpaRepository jpaRepository;
    private final CustomerPersistenceMapper mapper;

    public CustomerRepositoryImpl(CustomerJpaRepository jpaRepository, CustomerPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Customer> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(Customer customer) {
        jpaRepository.save(mapper.toEntity(customer));
    }
}
