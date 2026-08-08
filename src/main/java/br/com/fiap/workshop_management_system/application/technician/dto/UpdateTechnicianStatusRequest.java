package br.com.fiap.workshop_management_system.application.technician.dto;

import br.com.fiap.workshop_management_system.domain.technician.model.TechnicianStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTechnicianStatusRequest(@NotNull TechnicianStatus status) {
}
