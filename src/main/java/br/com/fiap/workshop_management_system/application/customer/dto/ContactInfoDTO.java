package br.com.fiap.workshop_management_system.application.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record ContactInfoDTO(
        @NotBlank String email,
        @NotBlank String phone) {
}
