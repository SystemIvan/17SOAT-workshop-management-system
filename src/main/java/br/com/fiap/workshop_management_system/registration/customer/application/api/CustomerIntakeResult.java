package br.com.fiap.workshop_management_system.registration.customer.application.api;

import java.util.UUID;

/**
 * {@code customerId} is {@code null} when the outcome is {@link CustomerIntakeOutcome#ARCHIVED}.
 */
public record CustomerIntakeResult(UUID customerId, CustomerIntakeOutcome outcome) {

    public static CustomerIntakeResult archived() {
        return new CustomerIntakeResult(null, CustomerIntakeOutcome.ARCHIVED);
    }
}
