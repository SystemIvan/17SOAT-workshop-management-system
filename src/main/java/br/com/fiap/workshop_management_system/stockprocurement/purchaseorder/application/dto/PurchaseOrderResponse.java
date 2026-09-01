package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String externalReference,
        PurchaseOrderStatusResponse status,
        List<PurchaseOrderLineResponse> lines,
        List<UUID> demandIds,
        Instant createdAt,
        Instant openedAt,
        Instant closedAt,
        UUID closedByUserAccountId,
        UUID receiptId,
        Instant receivedAt) {

    public PurchaseOrderResponse {
        lines = List.copyOf(lines);
        demandIds = List.copyOf(demandIds);
    }
}
