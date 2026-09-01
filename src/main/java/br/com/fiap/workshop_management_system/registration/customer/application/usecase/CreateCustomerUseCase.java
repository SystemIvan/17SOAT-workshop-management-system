package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.CreateCustomerRequest;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
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
        TaxId taxId = new TaxId(request.document());
        if (repository.existsByTaxId(taxId)) {
            throw new CustomerTaxIdAlreadyExistsException();
        }
        Customer customer = Customer.create(request.name(), taxId,
                CustomerMapper.toContactInfo(request.contactInfo()));
        repository.save(customer);
        return CustomerMapper.toResponse(customer);
    }
}
