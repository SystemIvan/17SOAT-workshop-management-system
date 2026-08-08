package br.com.fiap.workshop_management_system.application.parts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record UpdatePartPriceRequest(@NotNull @Valid PriceDTO price) {
}
