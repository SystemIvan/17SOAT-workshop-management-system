package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.notification;

import br.com.fiap.workshop_management_system.registration.customer.domain.model.Customer;

/**
 * The e-mail a Customer notification should be sent to, if any. Today {@code ContactInfo} always requires an
 * e-mail; the empty case is kept so the RF52 "customer without e-mail" rule keeps holding if that ever changes.
 * Shared by every customer notification adapter of {@code servicelifecycle}.
 */
public record CustomerEmail(String value) {

    public static CustomerEmail of(Customer customer) {
        if (customer.contactInfo() == null || customer.contactInfo().email() == null) {
            return new CustomerEmail(null);
        }
        return new CustomerEmail(customer.contactInfo().email().value());
    }

    public boolean isPresent() {
        return value != null && !value.isBlank();
    }
}
