package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogServiceExecutionTimeAggregate(
        UUID catalogServiceId,
        long sampleCount,
        BigDecimal averageHours) {
}
