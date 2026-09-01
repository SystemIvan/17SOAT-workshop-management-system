package br.com.fiap.workshop_management_system.registration.customer.infrastructure.persistence;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Address;
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
        Address address = contactInfo.address();
        return new CustomerJpaEntity(
                customer.id(),
                customer.name(),
                customer.taxId().value(),
                contactInfo.email().value(),
                contactInfo.phone().value(),
                address == null ? null : address.street(),
                address == null ? null : address.number(),
                address == null ? null : address.complement(),
                address == null ? null : address.neighborhood(),
                address == null ? null : address.city(),
                address == null ? null : address.state(),
                address == null ? null : address.postalCode(),
                customer.active());
    }

    public Customer toDomain(CustomerJpaEntity entity) {
        Address address = toAddress(entity);
        ContactInfo contactInfo = new ContactInfo(entity.getContactEmail(), entity.getContactPhone(), address);
        return Customer.reconstitute(entity.getId(), entity.getName(), new TaxId(entity.getDocument()), contactInfo,
                entity.isActive());
    }

    private static Address toAddress(CustomerJpaEntity entity) {
        if (entity.getAddressStreet() == null) {
            return null;
        }
        return new Address(
                entity.getAddressStreet(),
                entity.getAddressNumber(),
                entity.getAddressComplement(),
                entity.getAddressNeighborhood(),
                entity.getAddressCity(),
                entity.getAddressState(),
                entity.getAddressPostalCode());
    }
}
