package br.com.fiap.workshop_management_system.registration.vehicle.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.UUID;

public record CreateVehicleRequest(
        @NotNull(message = "O Customer é obrigatório")
        @Schema(example = "ca0416e2-86da-4eaa-b27e-d4a9262f51e6")
        UUID customerId,
        @NotBlank(message = "A placa não pode estar em branco")
        @Size(max = 16, message = "A placa deve possuir no máximo 16 caracteres")
        @Schema(description = "Placa brasileira legada ou Mercosul", example = "ABC-1234")
        String licensePlate,
        @Size(max = 32, message = "O chassis deve possuir no máximo 32 caracteres na entrada")
        @Pattern(regexp = ".*\\S.*", message = "O chassis informado não pode estar em branco")
        @Schema(nullable = true, example = "9BWZZZ377VT004251")
        String chassis,
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
        @JsonDeserialize(using = StrictLongDeserializer.class)
        @PositiveOrZero(message = "A quilometragem deve ser maior ou igual a zero")
        @Schema(
                description = "Quilometragem inicial opcional em quilômetros inteiros",
                nullable = true,
                types = {"integer", "null"},
                format = "int64",
                minimum = "0",
                example = "42500")
        Long mileage) {
}
