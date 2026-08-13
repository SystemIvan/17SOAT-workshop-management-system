package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Technician;

/**
 * Converts between the Technician aggregate and the application-layer DTOs.
 * Entities never cross the controller boundary directly.
 */
public final class TechnicianMapper {

    private TechnicianMapper() {
    }

    public static TechnicianResponse toResponse(Technician technician) {
        return new TechnicianResponse(technician.id(), technician.name(), technician.specialties(), technician.status());
    }
}
