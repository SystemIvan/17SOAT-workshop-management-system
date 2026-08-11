package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameCustomerRequest(@NotBlank String name) {
}
