package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.UpdateCustomerContactRequest;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
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
        customer.updateContactInfo(CustomerMapper.toContactInfo(request.contactInfo()));
        repository.save(customer);
        return CustomerMapper.toResponse(customer);
    }
}
