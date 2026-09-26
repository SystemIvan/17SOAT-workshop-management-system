package br.com.fiap.workshop_management_system.servicelifecycle.estimate.application.dto;

import jakarta.validation.constraints.NotNull;

/**
 * RF40 - body of {@code POST /api/service-orders/{serviceOrderId}/external-status-updates}.
 */
public record ExternalStatusUpdateRequest(@NotNull ExternalIntendedStatus intendedStatus) {
}
