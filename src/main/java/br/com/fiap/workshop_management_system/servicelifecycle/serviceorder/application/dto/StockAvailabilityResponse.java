package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.StockAvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

public record StockAvailabilityResponse(
        UUID stockItemId,
        int requestedQuantity,
        int observedAvailableQuantity,
        int shortageQuantity,
        StockAvailabilityStatus status,
        Instant observedAt) {
}
