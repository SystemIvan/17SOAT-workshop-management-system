package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import jakarta.validation.constraints.NotBlank;

public record ContactInfoDTO(
        @NotBlank(message = "O e-mail não pode estar em branco") String email,
        @NotBlank(message = "O telefone não pode estar em branco") String phone) {
}
