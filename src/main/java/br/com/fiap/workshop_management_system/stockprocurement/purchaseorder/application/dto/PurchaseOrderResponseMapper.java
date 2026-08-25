package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;

public final class PurchaseOrderResponseMapper {

    private PurchaseOrderResponseMapper() {
    }

    public static PurchaseOrderResponse toResponse(PurchaseOrder order) {
        return new PurchaseOrderResponse(
                order.id(),
                order.externalReference(),
                OpenPurchaseOrderStatus.OPEN,
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
                order.openedAt());
    }
}
