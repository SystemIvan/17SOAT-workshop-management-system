package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameCustomerRequest(@NotBlank(message = "O nome não pode estar em branco") String name) {
}
