package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdatePartPriceRequest(@NotNull @Valid PriceDTO price) {
}
