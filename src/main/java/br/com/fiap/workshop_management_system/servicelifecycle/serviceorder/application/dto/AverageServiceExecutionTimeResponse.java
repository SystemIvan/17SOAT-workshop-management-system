package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public record AverageServiceExecutionTimeResponse(
        Instant completedFromInclusive,
        Instant completedToExclusive,
        @Schema(allowableValues = "HOURS", example = "HOURS") String unit,
        ExecutionTimeAverageResponse global,
        List<CatalogServiceExecutionTimeAverageResponse> byCatalogService) {
}
