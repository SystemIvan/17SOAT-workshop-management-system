package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateCustomerContactRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateContactInfoDTO;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Address;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Email;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Phone;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateCustomerContactUseCase {

    private final CustomerRepository repository;

    public UpdateCustomerContactUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse execute(UUID id, UpdateCustomerContactRequest request) {
        Customer customer = CustomerFinder.getOrThrow(repository, id);
        UpdateContactInfoDTO update = request.contactInfo();
        Email email = update.email().map(Email::new).orElse(null);
        Phone phone = update.phone().map(Phone::new).orElse(null);
        Address address = update.address().map(CustomerMapper::toAddress).orElse(null);
        customer.updateContactInfo(email, phone, address);
        repository.save(customer);
        return CustomerMapper.toResponse(customer);
    }
}
