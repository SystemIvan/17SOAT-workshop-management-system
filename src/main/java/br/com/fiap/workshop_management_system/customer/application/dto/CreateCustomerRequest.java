package br.com.fiap.workshop_management_system.customer.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCustomerRequest(
        @NotBlank String name,
        @NotBlank String document,
        @NotNull @Valid ContactInfoDTO contactInfo) {
}
