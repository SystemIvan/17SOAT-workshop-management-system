package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Priority;

import java.util.UUID;

public record StockReservationRetryCandidate(
        UUID serviceOrderId,
        Priority priority,
        UUID serviceExecutionId) {
}
