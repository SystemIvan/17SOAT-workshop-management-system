package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.command;

import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderCommand(
        List<UUID> demandIds,
        List<PurchaseOrderLineCommand> lines) {

    public CreatePurchaseOrderCommand {
        demandIds = demandIds == null ? List.of() : List.copyOf(demandIds);
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
