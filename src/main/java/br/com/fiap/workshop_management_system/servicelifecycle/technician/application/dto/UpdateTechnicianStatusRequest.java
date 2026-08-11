package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.TechnicianStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTechnicianStatusRequest(@NotNull TechnicianStatus status) {
}
