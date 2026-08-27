package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;

public final class PurchaseOrderResponseMapper {

    private PurchaseOrderResponseMapper() {
    }

    public static PurchaseOrderResponse toResponse(PurchaseOrder order) {
        return toResponse(order, null);
    }

    public static PurchaseOrderResponse toResponse(PurchaseOrder order, StockReceipt receipt) {
        return new PurchaseOrderResponse(
                order.id(),
                order.externalReference(),
                mapStatus(order),
                order.lines().stream()
                        .map(line -> new PurchaseOrderLineResponse(
                                line.stockItemId(),
                                line.skuSnapshot(),
                                line.nameSnapshot(),
                                line.typeSnapshot(),
                                line.quantity()))
                        .toList(),
                order.selectedDemandIds().stream().sorted().toList(),
                order.createdAt(),
                order.openedAt(),
                order.closedAt(),
                order.closedByUserAccountId(),
                receipt == null ? null : receipt.id(),
                receipt == null ? null : receipt.receivedAt());
    }

    private static PurchaseOrderStatusResponse mapStatus(PurchaseOrder order) {
        return switch (order.status()) {
            case OPEN -> PurchaseOrderStatusResponse.OPEN;
            case CLOSED -> PurchaseOrderStatusResponse.CLOSED;
            default -> throw new IllegalArgumentException("Only confirmed purchase orders can be mapped to a response");
        };
    }
}
