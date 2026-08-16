package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCustomerRequest(
        @NotBlank(message = "O nome não pode estar em branco") String name,
        @NotBlank(message = "O CPF/CNPJ não pode estar em branco")
        @Schema(description = "CPF ou CNPJ, formatado ou apenas números", example = "529.982.247-25")
        String document,
        @NotNull(message = "As informações de contato são obrigatórias") @Valid ContactInfoDTO contactInfo) {
}
