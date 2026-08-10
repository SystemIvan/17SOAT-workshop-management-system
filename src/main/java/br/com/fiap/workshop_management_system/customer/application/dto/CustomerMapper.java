package br.com.fiap.workshop_management_system.customer.application.dto;

import br.com.fiap.workshop_management_system.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.customer.domain.model.Customer;

/**
 * Converts between the Customer aggregate and the application-layer DTOs.
 * Entities never cross the controller boundary directly.
 */
public final class CustomerMapper {

    private CustomerMapper() {
    }

    public static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.id(), customer.name(), customer.document(), toContactInfoDTO(customer.contactInfo()));
    }

    public static ContactInfo toContactInfo(ContactInfoDTO dto) {
        return new ContactInfo(dto.email(), dto.phone());
    }

    public static ContactInfoDTO toContactInfoDTO(ContactInfo contactInfo) {
        return new ContactInfoDTO(contactInfo.email(), contactInfo.phone());
    }
}
