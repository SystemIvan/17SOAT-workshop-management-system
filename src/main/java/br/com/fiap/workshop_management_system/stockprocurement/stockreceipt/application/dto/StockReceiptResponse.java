package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StockReceiptResponse(
        UUID id,
        UUID purchaseOrderId,
        UUID receivedByUserAccountId,
        Instant receivedAt,
        List<StockReceiptLineResponse> lines) {

    public StockReceiptResponse {
        lines = List.copyOf(lines);
    }
}
