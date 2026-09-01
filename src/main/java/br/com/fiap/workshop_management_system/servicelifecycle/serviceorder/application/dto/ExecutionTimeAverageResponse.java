package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record ExecutionTimeAverageResponse(
        @Schema(example = "3") long sampleCount,
        @Schema(example = "1.50", nullable = true) BigDecimal averageHours) {
}
