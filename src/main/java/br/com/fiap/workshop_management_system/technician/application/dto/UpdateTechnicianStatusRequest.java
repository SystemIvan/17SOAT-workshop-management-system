package br.com.fiap.workshop_management_system.technician.application.dto;

import br.com.fiap.workshop_management_system.technician.domain.model.TechnicianStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTechnicianStatusRequest(@NotNull TechnicianStatus status) {
}
