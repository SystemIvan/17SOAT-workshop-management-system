package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command.PurchaseOrderLineCommand;

import java.util.List;
import java.util.UUID;

record NormalizedPurchaseOrderCommand(
        List<UUID> demandIds,
        List<PurchaseOrderLineCommand> lines,
        String payloadHash) {

    NormalizedPurchaseOrderCommand {
        demandIds = List.copyOf(demandIds);
        lines = List.copyOf(lines);
    }
}
