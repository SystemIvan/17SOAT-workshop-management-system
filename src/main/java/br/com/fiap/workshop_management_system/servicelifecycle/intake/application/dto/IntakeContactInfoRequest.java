package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record IntakeContactInfoRequest(
        @Email(message = "O e-mail deve ser válido")
        @Size(max = 254, message = "O e-mail deve ter até 254 caracteres")
        String email,
        @Size(max = 32, message = "O telefone deve ter até 32 caracteres")
        @Schema(description = "Telefone brasileiro ou internacional E.164", example = "+5511999999999")
        String phone,
        @Schema(nullable = true) @Valid IntakeAddressRequest address) {
}
