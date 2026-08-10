package br.com.fiap.workshop_management_system.technician.application.dto;

import br.com.fiap.workshop_management_system.technician.domain.model.Specialty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateTechnicianRequest(
        @NotBlank String name,
        @NotEmpty Set<Specialty> specialties) {
}
