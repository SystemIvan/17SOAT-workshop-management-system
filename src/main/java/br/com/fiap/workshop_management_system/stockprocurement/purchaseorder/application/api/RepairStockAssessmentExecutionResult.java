package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RepairStockAssessmentExecutionResult(UUID serviceExecutionId, List<RepairStockAssessmentResultLine> lines) {

    public RepairStockAssessmentExecutionResult {
        Objects.requireNonNull(serviceExecutionId, "serviceExecutionId must not be null");
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
