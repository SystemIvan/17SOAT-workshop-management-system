package br.com.fiap.workshop_management_system.application.customer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdateCustomerContactRequest(@NotNull @Valid ContactInfoDTO contactInfo) {
}
