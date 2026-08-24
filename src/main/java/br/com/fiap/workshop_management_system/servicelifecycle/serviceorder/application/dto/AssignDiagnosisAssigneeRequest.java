package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignDiagnosisAssigneeRequest(@NotNull UUID technicianId) {
}
