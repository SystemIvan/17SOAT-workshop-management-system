package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;

/**
 * Converte o agregado Customer e os DTOs da camada de aplicação.
 * Entidades nunca atravessam diretamente a fronteira do controller.
 */
public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.id(), customer.name(), customer.taxId().value(),
                toContactInfoDTO(customer.contactInfo()));
    }

    public static ContactInfo toContactInfo(ContactInfoDTO dto) {
        return new ContactInfo(dto.email(), dto.phone());
    }

    public static ContactInfoDTO toContactInfoDTO(ContactInfo contactInfo) {
        return new ContactInfoDTO(contactInfo.email(), contactInfo.phone());
    }
}
