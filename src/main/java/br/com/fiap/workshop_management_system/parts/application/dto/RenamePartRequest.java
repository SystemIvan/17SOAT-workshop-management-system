package br.com.fiap.workshop_management_system.parts.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RenamePartRequest(@NotBlank String name) {
}
