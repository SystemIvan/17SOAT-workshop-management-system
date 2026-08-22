package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.event;

import java.util.UUID;

public record TechnicianMaterialsReservedEvent(
        UUID serviceOrderId,
        UUID serviceExecutionId,
        UUID technicianId,
        UUID stockReservationId) {
}
