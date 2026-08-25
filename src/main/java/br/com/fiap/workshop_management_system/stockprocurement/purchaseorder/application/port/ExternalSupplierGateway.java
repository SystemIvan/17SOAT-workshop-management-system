package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port;

public interface ExternalSupplierGateway {

    ExternalPurchaseOrderResult submit(ExternalPurchaseOrderCommand command);
}
