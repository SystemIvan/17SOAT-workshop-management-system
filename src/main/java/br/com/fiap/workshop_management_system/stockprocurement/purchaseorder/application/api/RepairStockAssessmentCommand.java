package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

import java.util.List;

public record RepairStockAssessmentCommand(List<RepairStockAssessmentExecution> executions) {

    public RepairStockAssessmentCommand {
        executions = executions == null ? List.of() : List.copyOf(executions);
    }
}
