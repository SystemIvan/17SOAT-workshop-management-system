package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.application.dto;

import java.util.UUID;

public record StockReservationLineResponse(UUID stockItemId, int quantity) {
}
