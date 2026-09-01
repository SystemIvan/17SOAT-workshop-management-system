package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;

public final class StockReservationMapper {

    private StockReservationMapper() {
    }

    public static StockReservationResponse toResponse(StockReservation reservation) {
        return new StockReservationResponse(
                reservation.id(),
                reservation.serviceExecutionId(),
                reservation.status(),
                reservation.lines().stream()
                        .map(line -> new StockReservationLineResponse(line.stockItemId(), line.quantity()))
                        .toList(),
                reservation.createdAt(),
                reservation.consumedAt());
    }
}
