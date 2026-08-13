package br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto;

import jakarta.validation.constraints.NotBlank;

public record RenamePartRequest(@NotBlank String name) {
}
