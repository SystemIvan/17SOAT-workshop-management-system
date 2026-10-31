package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import java.util.UUID;

/**
 * Identifies or registers the Customer of a full Service Order intake inside the current transaction.
 *
 * <p>Implementations report invalid registration data with
 * {@link br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
 * .ServiceOrderIntakeInvalidDataException} and a concurrent registration with
 * {@link br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.exception
 * .ServiceOrderIntakeConcurrentRegistrationException}.
 */
public interface CustomerIntakePort {

    CustomerIntake identifyOrRegister(CustomerData data);

    record CustomerData(String document, String name, String email, String phone, AddressData address) {
    }

    record AddressData(
            String street,
            String number,
            String complement,
            String neighborhood,
            String city,
            String state,
            String postalCode) {
    }

    /**
     * {@code customerId} is {@code null} when the status is {@link Status#ARCHIVED}.
     */
    record CustomerIntake(UUID customerId, Status status) {
    }

    enum Status {
        REUSED,
        CREATED,
        ARCHIVED
    }
}
