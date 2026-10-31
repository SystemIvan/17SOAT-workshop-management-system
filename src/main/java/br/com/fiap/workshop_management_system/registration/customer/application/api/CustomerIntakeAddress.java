package br.com.fiap.workshop_management_system.registration.customer.application.api;

public record CustomerIntakeAddress(
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String postalCode) {
}
