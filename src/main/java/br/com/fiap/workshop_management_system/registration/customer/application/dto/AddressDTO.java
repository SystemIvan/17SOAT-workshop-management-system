package br.com.fiap.workshop_management_system.registration.customer.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressDTO(
        @NotBlank(message = "O logradouro não pode estar em branco")
        @Size(max = 255, message = "O logradouro deve ter até 255 caracteres")
        @Schema(description = "Logradouro", example = "Avenida Paulista")
        String street,
        @NotBlank(message = "O número não pode estar em branco")
        @Size(max = 255, message = "O número deve ter até 255 caracteres")
        @Schema(description = "Número ou identificação do imóvel", example = "1000")
        String number,
        @Size(max = 255, message = "O complemento deve ter até 255 caracteres")
        @Schema(description = "Complemento opcional", example = "Conjunto 101")
        String complement,
        @Size(max = 255, message = "O bairro deve ter até 255 caracteres")
        @Schema(description = "Bairro opcional", example = "Bela Vista")
        String neighborhood,
        @NotBlank(message = "A cidade não pode estar em branco")
        @Size(max = 255, message = "A cidade deve ter até 255 caracteres")
        @Schema(description = "Cidade", example = "São Paulo")
        String city,
        @NotBlank(message = "A UF não pode estar em branco")
        @Pattern(regexp = "(?i)AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO",
                message = "A UF deve ser uma sigla brasileira válida")
        @Schema(description = "Unidade federativa brasileira", example = "SP")
        String state,
        @NotBlank(message = "O CEP não pode estar em branco")
        @Pattern(regexp = "\\d{5}-?\\d{3}", message = "O CEP deve conter oito dígitos, com hífen opcional")
        @Schema(description = "CEP com ou sem hífen", example = "01310-100")
        String postalCode) {
}
