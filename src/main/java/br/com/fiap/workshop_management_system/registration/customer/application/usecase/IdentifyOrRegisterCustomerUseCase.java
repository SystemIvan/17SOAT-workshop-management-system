package br.com.fiap.workshop_management_system.registration.customer.application.usecase;

import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeAddress;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeApi;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeCommand;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeOutcome;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeResult;
import br.com.fiap.workshop_management_system.registration.customer.application.exception
        .CustomerTaxIdAlreadyExistsException;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Address;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.ContactInfo;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;
import br.com.fiap.workshop_management_system.registration.customer.domain.model.TaxId;
import br.com.fiap.workshop_management_system.registration.customer.domain.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class IdentifyOrRegisterCustomerUseCase implements CustomerIntakeApi {

    private final CustomerRepository repository;

    public IdentifyOrRegisterCustomerUseCase(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerIntakeResult identifyOrRegister(CustomerIntakeCommand command) {
        Objects.requireNonNull(command, "Os dados do cliente são obrigatórios");
        TaxId taxId = new TaxId(command.document());
        return repository.findActiveByTaxId(taxId)
                .map(customer -> new CustomerIntakeResult(customer.id(), CustomerIntakeOutcome.REUSED))
                .orElseGet(() -> archivedOrRegister(taxId, command));
    }

    private CustomerIntakeResult archivedOrRegister(TaxId taxId, CustomerIntakeCommand command) {
        if (repository.existsByTaxId(taxId)) {
            return CustomerIntakeResult.archived();
        }
        Customer customer = Customer.create(command.name(), taxId, toContactInfo(command));
        try {
            repository.save(customer);
        } catch (CustomerTaxIdAlreadyExistsException exception) {
            throw new CustomerIntakeConflictException();
        }
        return new CustomerIntakeResult(customer.id(), CustomerIntakeOutcome.CREATED);
    }

    private static ContactInfo toContactInfo(CustomerIntakeCommand command) {
        if (command.email() == null || command.phone() == null) {
            throw new IllegalArgumentException("E-mail e telefone de contato devem ser informados");
        }
        return new ContactInfo(command.email(), command.phone(), toAddress(command.address()));
    }

    private static Address toAddress(CustomerIntakeAddress address) {
        if (address == null) {
            return null;
        }
        return new Address(address.street(), address.number(), address.complement(), address.neighborhood(),
                address.city(), address.state(), address.postalCode());
    }
}
