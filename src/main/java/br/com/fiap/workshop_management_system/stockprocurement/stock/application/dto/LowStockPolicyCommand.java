package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

public record LowStockPolicyCommand(Integer minimumQuantity, Integer targetQuantity) {

    public LowStockPolicyCommand {
        if (minimumQuantity == null || targetQuantity == null) {
            throw new IllegalArgumentException("Minimum and target quantities must be provided together");
        }
    }
}
