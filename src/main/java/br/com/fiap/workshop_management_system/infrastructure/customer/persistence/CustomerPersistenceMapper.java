package br.com.fiap.workshop_management_system.infrastructure.customer.persistence;

import br.com.fiap.workshop_management_system.domain.customer.model.ContactInfo;
import br.com.fiap.workshop_management_system.domain.customer.model.Customer;
import org.springframework.stereotype.Component;

/**
 * Converts between the framework-agnostic {@link Customer} aggregate and its JPA
 * projection. Reconstruction of the domain object goes through {@link Customer#reconstitute},
 * which restores exact persisted state without re-running creation rules.
 */
@Component
public class CustomerPersistenceMapper {

    public CustomerJpaEntity toEntity(Customer customer) {
        ContactInfo contactInfo = customer.contactInfo();
        return new CustomerJpaEntity(customer.id(), customer.name(), customer.document(), contactInfo.email(), contactInfo.phone());
    }

    public Customer toDomain(CustomerJpaEntity entity) {
        ContactInfo contactInfo = new ContactInfo(entity.getContactEmail(), entity.getContactPhone());
        return Customer.reconstitute(entity.getId(), entity.getName(), entity.getDocument(), contactInfo);
    }
}
