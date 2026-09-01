package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogServiceExecutionTimeAverageResponse(
        UUID catalogServiceId,
        @Schema(example = "2") long sampleCount,
        @Schema(example = "1.25") BigDecimal averageHours) {
}
