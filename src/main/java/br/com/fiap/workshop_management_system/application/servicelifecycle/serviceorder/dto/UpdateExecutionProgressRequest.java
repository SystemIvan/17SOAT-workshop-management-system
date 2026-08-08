package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateExecutionProgressRequest(
        @NotBlank String note) {
}
