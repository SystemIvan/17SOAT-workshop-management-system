package br.com.fiap.workshop_management_system.application.servicelifecycle.serviceorder.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignTechnicianRequest(
        @NotNull UUID technicianId) {
}
