package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateExecutionProgressRequest(
        @NotBlank String note) {
}
