package br.com.fiap.workshop_management_system.registration.customer.application.api;

/**
 * Name and contact data are only used when no Customer exists for the document; they never update an existing one.
 */
public record CustomerIntakeCommand(
        String document,
        String name,
        String email,
        String phone,
        CustomerIntakeAddress address) {
}
