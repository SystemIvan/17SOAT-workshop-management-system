package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

public record LowStockPolicyResponse(int minimumQuantity, int targetQuantity) {
}
