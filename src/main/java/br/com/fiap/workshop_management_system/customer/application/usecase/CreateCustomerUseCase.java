package br.com.fiap.workshop_management_system.customer.application.usecase;

import br.com.fiap.workshop_management_system.customer.application.dto.CreateCustomerRequest;
import br.com.fiap.workshop_management_system.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateCustomerUseCase {

    private final CustomerRepository repository;

    public CreateCustomerUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse execute(CreateCustomerRequest request) {
        Customer customer = Customer.create(request.name(), request.document(), CustomerMapper.toContactInfo(request.contactInfo()));
        repository.save(customer);
        return CustomerMapper.toResponse(customer);
    }
}
