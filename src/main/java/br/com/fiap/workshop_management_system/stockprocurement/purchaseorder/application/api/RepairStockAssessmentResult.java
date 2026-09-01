package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.util.List;

public record RepairStockAssessmentResult(List<RepairStockAssessmentExecutionResult> executions) {

    public RepairStockAssessmentResult {
        executions = executions == null ? List.of() : List.copyOf(executions);
    }
}
