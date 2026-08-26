package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record LowStockPolicyRequest(
        @NotNull @PositiveOrZero Integer minimumQuantity,
        @NotNull @Positive Integer targetQuantity) {

    public LowStockPolicyCommand toCommand() {
        return new LowStockPolicyCommand(minimumQuantity, targetQuantity);
    }
}
