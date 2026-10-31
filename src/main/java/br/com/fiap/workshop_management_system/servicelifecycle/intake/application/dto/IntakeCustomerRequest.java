package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code name} and {@code contactInfo} are only required, and only used, when the document is not registered yet.
 */
public record IntakeCustomerRequest(
        @NotBlank(message = "O CPF/CNPJ não pode estar em branco")
        @Schema(description = "CPF ou CNPJ, formatado ou apenas números", example = "529.982.247-25")
        String document,
        @Schema(nullable = true) String name,
        @Schema(nullable = true) @Valid IntakeContactInfoRequest contactInfo) {
}
