package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Address;
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
                toContactInfoDTO(customer.contactInfo()), customer.active());
    }

    public static ContactInfo toContactInfo(ContactInfoDTO dto) {
        return new ContactInfo(dto.email(), dto.phone(), toAddress(dto.address()));
    }

    public static ContactInfoDTO toContactInfoDTO(ContactInfo contactInfo) {
        return new ContactInfoDTO(contactInfo.email().value(), contactInfo.phone().value(),
                toAddressDTO(contactInfo.address()));
    }

    public static Address toAddress(AddressDTO dto) {
        if (dto == null) {
            return null;
        }
        return new Address(dto.street(), dto.number(), dto.complement(), dto.neighborhood(), dto.city(), dto.state(),
                dto.postalCode());
    }

    private static AddressDTO toAddressDTO(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressDTO(address.street(), address.number(), address.complement(), address.neighborhood(),
                address.city(), address.state(), address.postalCode());
    }
}
