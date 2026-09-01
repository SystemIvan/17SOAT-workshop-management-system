package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReservationResponse(
        UUID id,
        UUID serviceExecutionId,
        StockReservationStatus status,
        List<StockReservationLineResponse> lines,
        Instant createdAt,
        Instant consumedAt
) {
}
