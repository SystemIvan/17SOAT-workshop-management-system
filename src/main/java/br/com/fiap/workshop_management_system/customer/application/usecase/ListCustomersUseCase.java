package br.com.fiap.workshop_management_system.customer.application.usecase;

import br.com.fiap.workshop_management_system.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListCustomersUseCase {

    private final CustomerRepository repository;

    public ListCustomersUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> execute() {
        return repository.findAll().stream().map(CustomerMapper::toResponse).toList();
    }
}
