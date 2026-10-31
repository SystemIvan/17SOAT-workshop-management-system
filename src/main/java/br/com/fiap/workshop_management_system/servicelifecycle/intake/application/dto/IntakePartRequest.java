package br.com.fiap.workshop_management_system.servicelifecycle.intake.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record IntakePartRequest(
        @NotNull UUID stockItemId,
        @Positive(message = "A quantidade da peça deve ser positiva") int quantity) {
}
