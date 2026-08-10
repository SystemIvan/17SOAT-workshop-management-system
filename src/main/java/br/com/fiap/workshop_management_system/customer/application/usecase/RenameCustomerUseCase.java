package br.com.fiap.workshop_management_system.customer.application.usecase;

import br.com.fiap.workshop_management_system.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.customer.application.dto.RenameCustomerRequest;
import br.com.fiap.workshop_management_system.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RenameCustomerUseCase {

    private final CustomerRepository repository;

    public RenameCustomerUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CustomerResponse execute(UUID id, RenameCustomerRequest request) {
        Customer customer = CustomerFinder.getOrThrow(repository, id);
        customer.rename(request.name());
        repository.save(customer);
        return CustomerMapper.toResponse(customer);
    }
}
