package br.com.fiap.workshop_management_system.application.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameCustomerRequest(@NotBlank String name) {
}
