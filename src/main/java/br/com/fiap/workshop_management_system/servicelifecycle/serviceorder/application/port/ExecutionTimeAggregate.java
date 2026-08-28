package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.application.port;

import java.math.BigDecimal;

public record ExecutionTimeAggregate(long sampleCount, BigDecimal averageHours) {
}
