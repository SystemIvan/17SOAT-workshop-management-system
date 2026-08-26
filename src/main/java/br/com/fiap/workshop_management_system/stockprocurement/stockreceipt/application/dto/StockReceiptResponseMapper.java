package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderLine;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.exception.StockReceiptInconsistentException;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class StockReceiptResponseMapper {

    private StockReceiptResponseMapper() {
    }

    public static StockReceiptResponse toResponse(StockReceipt receipt, PurchaseOrder purchaseOrder) {
        Map<java.util.UUID, PurchaseOrderLine> linesByStockItem = purchaseOrder.lines().stream()
                .collect(Collectors.toMap(PurchaseOrderLine::stockItemId, Function.identity()));
        return new StockReceiptResponse(
                receipt.id(),
                receipt.purchaseOrderId(),
                receipt.receivedByUserAccountId(),
                receipt.receivedAt(),
                receipt.lines().stream().map(line -> {
                    PurchaseOrderLine purchaseOrderLine = linesByStockItem.get(line.stockItemId());
                    if (purchaseOrderLine == null) {
                        throw new StockReceiptInconsistentException();
                    }
                    return new StockReceiptLineResponse(
                            line.movementId(),
                            line.stockItemId(),
                            purchaseOrderLine.skuSnapshot(),
                            purchaseOrderLine.nameSnapshot(),
                            purchaseOrderLine.typeSnapshot(),
                            line.quantity(),
                            line.availableBefore(),
                            line.availableAfter());
                }).toList());
    }
}
