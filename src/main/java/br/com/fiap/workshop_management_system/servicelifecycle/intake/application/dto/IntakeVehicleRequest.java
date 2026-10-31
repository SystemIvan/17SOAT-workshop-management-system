package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Only the plate identifies the Vehicle. The other fields are required, and only used, for a new registration.
 */
public record IntakeVehicleRequest(
        @NotBlank(message = "A placa não pode estar em branco")
        @Size(max = 16, message = "A placa deve possuir no máximo 16 caracteres")
        @Schema(description = "Placa brasileira legada ou Mercosul", example = "ABC1D23")
        String licensePlate,
        @Size(max = 100, message = "A marca deve possuir no máximo 100 caracteres")
        String brand,
        @Size(max = 100, message = "O modelo deve possuir no máximo 100 caracteres")
        String model,
        @Min(value = 1886, message = "O ano deve ser igual ou posterior a 1886")
        Integer year,
        @Size(max = 50, message = "A cor deve possuir no máximo 50 caracteres")
        String color,
        @Size(max = 32, message = "O chassis deve possuir no máximo 32 caracteres na entrada")
        @Pattern(regexp = ".*\\S.*", message = "O chassis informado não pode estar em branco")
        @Schema(nullable = true)
        String chassis,
        @PositiveOrZero(message = "A quilometragem deve ser maior ou igual a zero")
        @Schema(nullable = true)
        Long mileage) {
}
