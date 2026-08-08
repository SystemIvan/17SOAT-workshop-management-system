package br.com.fiap.workshop_management_system.application.technician.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameTechnicianRequest(@NotBlank String name) {
}
