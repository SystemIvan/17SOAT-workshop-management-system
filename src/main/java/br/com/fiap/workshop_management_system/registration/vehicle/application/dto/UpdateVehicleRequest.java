package br.com.fiap.workshop_management_system.registration.vehicle.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVehicleRequest(
        @NotBlank(message = "A marca não pode estar em branco")
        @Size(max = 100, message = "A marca deve possuir no máximo 100 caracteres")
        String brand,
        @NotBlank(message = "O modelo não pode estar em branco")
        @Size(max = 100, message = "O modelo deve possuir no máximo 100 caracteres")
        String model,
        @NotNull(message = "O ano é obrigatório")
        @Min(value = 1886, message = "O ano deve ser igual ou posterior a 1886")
        Integer year,
        @NotBlank(message = "A cor não pode estar em branco")
        @Size(max = 50, message = "A cor deve possuir no máximo 50 caracteres")
        String color,
        @Schema(
                description = "Novo chassis; omissão, null, vazio ou espaços preservam o valor atual",
                nullable = true,
                example = "9BWZZZ377VT004251")
        String chassis) {
}
