package br.com.fiap.workshop_management_system.serviceorder.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateExecutionProgressRequest(
        @NotBlank String note) {
}
