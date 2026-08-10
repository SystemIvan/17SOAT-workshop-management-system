package br.com.fiap.workshop_management_system.customer.application.usecase;

import br.com.fiap.workshop_management_system.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetCustomerUseCase {

    private final CustomerRepository repository;

    public GetCustomerUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CustomerResponse execute(UUID id) {
        Customer customer = CustomerFinder.getOrThrow(repository, id);
        return CustomerMapper.toResponse(customer);
    }
}
