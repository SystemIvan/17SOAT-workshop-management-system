package br.com.fiap.workshop_management_system.application.technician.dto;

import br.com.fiap.workshop_management_system.domain.technician.model.Specialty;
import br.com.fiap.workshop_management_system.domain.technician.model.TechnicianStatus;

import java.util.Set;
import java.util.UUID;

public record TechnicianResponse(
        UUID id,
        String name,
        Set<Specialty> specialties,
        TechnicianStatus status) {
}
