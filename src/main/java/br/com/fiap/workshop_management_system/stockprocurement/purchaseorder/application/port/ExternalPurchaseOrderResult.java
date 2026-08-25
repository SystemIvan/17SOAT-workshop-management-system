package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port;

public sealed interface ExternalPurchaseOrderResult
        permits ExternalPurchaseOrderResult.Accepted, ExternalPurchaseOrderResult.Rejected {

    record Accepted(String externalReference) implements ExternalPurchaseOrderResult {
    }

    record Rejected(String rejectionCode) implements ExternalPurchaseOrderResult {
    }
}
