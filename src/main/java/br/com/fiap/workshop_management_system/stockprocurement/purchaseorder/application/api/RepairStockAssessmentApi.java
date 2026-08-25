package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.api;

public interface RepairStockAssessmentApi {

    RepairStockAssessmentResult assessAndRecord(RepairStockAssessmentCommand command);
}
