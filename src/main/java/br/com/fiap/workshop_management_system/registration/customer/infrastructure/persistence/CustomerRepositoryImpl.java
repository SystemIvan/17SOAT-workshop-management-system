package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter de infraestrutura da porta {@link CustomerRepository}, implementado com JPA.
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
    public Optional<Customer> findActiveByTaxId(TaxId taxId) {
        return jpaRepository.findByDocumentAndActiveTrue(taxId.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsByTaxId(TaxId taxId) {
        return jpaRepository.existsByDocument(taxId.value());
    }

    @Override
    public List<Customer> findAllActive() {
        return jpaRepository.findAllByActiveTrue().stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(Customer customer) {
        try {
            jpaRepository.saveAndFlush(mapper.toEntity(customer));
        } catch (DataIntegrityViolationException exception) {
            if (isTaxIdUniquenessViolation(exception)) {
                throw new CustomerTaxIdAlreadyExistsException();
            }
            throw exception;
        }
    }

    private static boolean isTaxIdUniquenessViolation(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("uk_customers_document");
    }
}
