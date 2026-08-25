package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.port;

import java.util.List;
import java.util.UUID;

public record ExternalPurchaseOrderCommand(
        UUID purchaseOrderId,
        UUID idempotencyKey,
        List<ExternalPurchaseOrderLine> lines) {

    public ExternalPurchaseOrderCommand {
        lines = List.copyOf(lines);
    }
}
