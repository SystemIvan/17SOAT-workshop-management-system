package br.com.fiap.workshop_management_system.servicelifecycle.technician.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.Specialty;
import br.com.fiap.workshop_management_system.servicelifecycle.technician.domain.model.TechnicianStatus;

import java.util.Set;
import java.util.UUID;

public record TechnicianResponse(
        UUID id,
        String name,
        Set<Specialty> specialties,
        TechnicianStatus status) {
}
