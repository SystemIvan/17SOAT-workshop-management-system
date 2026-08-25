package br.com.fiap.workshop_management_system.registration.vehicle.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.annotation.JsonDeserialize;

public record UpdateVehicleMileageRequest(
        @NotNull(message = "A quilometragem é obrigatória")
        @PositiveOrZero(message = "A quilometragem deve ser maior ou igual a zero")
        @JsonDeserialize(using = StrictLongDeserializer.class)
        @Schema(
                description = "Leitura absoluta do hodômetro em quilômetros inteiros",
                types = "integer",
                format = "int64",
                minimum = "0",
                example = "43120")
        Long mileage) {
}
