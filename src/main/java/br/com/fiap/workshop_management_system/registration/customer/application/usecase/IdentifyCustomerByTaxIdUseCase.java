package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerMapper;
import br.com.fiap.workshop_management_system.registration.customer.application.dto.CustomerResponse;
import br.com.fiap.workshop_management_system.registration.customer.application.exception.CustomerNotFoundException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentifyCustomerByTaxIdUseCase {

    private final CustomerRepository repository;

    public IdentifyCustomerByTaxIdUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public CustomerResponse execute(String document) {
        TaxId taxId = new TaxId(document);
        return repository.findActiveByTaxId(taxId)
                .map(CustomerMapper::toResponse)
                .orElseThrow(CustomerNotFoundException::new);
    }
}
