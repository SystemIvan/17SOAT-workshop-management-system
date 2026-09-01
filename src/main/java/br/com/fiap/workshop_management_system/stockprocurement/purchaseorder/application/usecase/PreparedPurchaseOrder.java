package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;

record PreparedPurchaseOrder(PurchaseOrder purchaseOrder, boolean newlyPrepared) {
}
