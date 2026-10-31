package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.registration;

import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeAddress;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeApi;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeCommand;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeConflictException;
import br.com.fiap.workshop_management_system.registration.customer.application.api.CustomerIntakeResult;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeConcurrentRegistrationException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
        .ServiceOrderIntakeInvalidDataException;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port.CustomerIntakePort;
import org.springframework.stereotype.Component;

@Component
public class RegistrationCustomerIntakeAdapter implements CustomerIntakePort {

    private final CustomerIntakeApi customerIntakeApi;

    public RegistrationCustomerIntakeAdapter(CustomerIntakeApi customerIntakeApi) {
        this.customerIntakeApi = customerIntakeApi;
    }

    @Override
    public CustomerIntake identifyOrRegister(CustomerData data) {
        CustomerIntakeResult result;
        try {
            result = customerIntakeApi.identifyOrRegister(toCommand(data));
        } catch (IllegalArgumentException exception) {
            // Registration validation messages never echo the submitted document or contact values.
            throw new ServiceOrderIntakeInvalidDataException(exception.getMessage());
        } catch (CustomerIntakeConflictException exception) {
            throw new ServiceOrderIntakeConcurrentRegistrationException();
        }
        Status status = switch (result.outcome()) {
            case REUSED -> Status.REUSED;
            case CREATED -> Status.CREATED;
            case ARCHIVED -> Status.ARCHIVED;
        };
        return new CustomerIntake(result.customerId(), status);
    }

    private static CustomerIntakeCommand toCommand(CustomerData data) {
        AddressData address = data.address();
        CustomerIntakeAddress intakeAddress = address == null ? null : new CustomerIntakeAddress(
                address.street(), address.number(), address.complement(), address.neighborhood(), address.city(),
                address.state(), address.postalCode());
        return new CustomerIntakeCommand(data.document(), data.name(), data.email(), data.phone(), intakeAddress);
    }
}
