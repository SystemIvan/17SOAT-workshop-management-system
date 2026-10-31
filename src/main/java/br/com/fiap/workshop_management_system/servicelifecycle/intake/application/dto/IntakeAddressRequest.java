package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import jakarta.validation.constraints.Size;

/**
 * Required fields, state and postal code formats are validated by the Customer domain.
 */
public record IntakeAddressRequest(
        @Size(max = 255) String street,
        @Size(max = 255) String number,
        @Size(max = 255) String complement,
        @Size(max = 255) String neighborhood,
        @Size(max = 255) String city,
        @Size(max = 2) String state,
        @Size(max = 9) String postalCode) {
}
