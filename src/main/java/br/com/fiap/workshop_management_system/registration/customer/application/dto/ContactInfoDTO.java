package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactInfoDTO(
        @NotBlank(message = "O e-mail não pode estar em branco")
        @Email(message = "O e-mail deve ser válido")
        @Size(max = 254, message = "O e-mail deve ter até 254 caracteres")
        String email,
        @NotBlank(message = "O telefone não pode estar em branco")
        @Size(max = 32, message = "O telefone deve ter até 32 caracteres")
        @Schema(description = "Telefone brasileiro ou internacional E.164", example = "+5511999999999")
        String phone,
        @Valid
        @Schema(description = "Endereço postal brasileiro opcional", nullable = true)
        AddressDTO address) {

    public ContactInfoDTO(String email, String phone) {
        this(email, phone, null);
    }
}
