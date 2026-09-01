package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameTechnicianRequest(@NotBlank String name) {
}
