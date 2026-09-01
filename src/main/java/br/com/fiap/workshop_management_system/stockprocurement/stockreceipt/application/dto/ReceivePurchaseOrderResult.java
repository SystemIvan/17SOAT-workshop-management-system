package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;

public record ReceivePurchaseOrderResult(StockReceipt receipt, boolean created) {
}
