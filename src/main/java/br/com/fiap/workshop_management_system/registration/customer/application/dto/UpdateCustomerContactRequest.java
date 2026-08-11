package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateCustomerContactRequest(@NotNull @Valid ContactInfoDTO contactInfo) {
}
