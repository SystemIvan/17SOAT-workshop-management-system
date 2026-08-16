package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import org.springframework.stereotype.Component;

/**
 * Converte o agregado {@link Customer}, independente de framework, e sua projeção JPA.
 * A reconstrução do objeto de domínio usa {@link Customer#reconstitute}, restaurando
 * exatamente o estado persistido sem executar novamente as regras de criação.
 */
@Component
public class CustomerPersistenceMapper {

    public CustomerJpaEntity toEntity(Customer customer) {
        ContactInfo contactInfo = customer.contactInfo();
        return new CustomerJpaEntity(customer.id(), customer.name(), customer.taxId().value(), contactInfo.email(),
                contactInfo.phone());
    }

    public Customer toDomain(CustomerJpaEntity entity) {
        ContactInfo contactInfo = new ContactInfo(entity.getContactEmail(), entity.getContactPhone());
        return Customer.reconstitute(entity.getId(), entity.getName(), new TaxId(entity.getDocument()), contactInfo);
    }
}
