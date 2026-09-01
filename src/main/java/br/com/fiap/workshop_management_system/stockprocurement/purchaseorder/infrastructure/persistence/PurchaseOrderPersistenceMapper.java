package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderLine;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;

@Component
public class PurchaseOrderPersistenceMapper {

    public PurchaseOrderJpaEntity toEntity(PurchaseOrder order) {
        return new PurchaseOrderJpaEntity(
                order.id(),
                order.idempotencyKey(),
                order.payloadHash(),
                order.status(),
                order.externalReference(),
                order.supplierRejectionCode(),
                order.createdAt(),
                order.updatedAt(),
                order.openedAt(),
                order.closedAt(),
                order.closedByUserAccountId(),
                order.lines().stream()
                        .map(line -> new PurchaseOrderLineEmbeddable(
                                line.stockItemId(),
                                line.skuSnapshot(),
                                line.nameSnapshot(),
                                line.typeSnapshot(),
                                line.quantity()))
                        .toList(),
                new LinkedHashSet<>(order.selectedDemandIds()));
    }

    public PurchaseOrder toDomain(PurchaseOrderJpaEntity entity) {
        return PurchaseOrder.reconstitute(
                entity.getId(),
                entity.getIdempotencyKey(),
                entity.getPayloadHash(),
                entity.getStatus(),
                entity.getLines().stream()
                        .map(line -> new PurchaseOrderLine(
                                line.getStockItemId(),
                                line.getSku(),
                                line.getName(),
                                line.getType(),
                                line.getQuantity()))
                        .sorted(Comparator.comparing(PurchaseOrderLine::stockItemId))
                        .toList(),
                entity.getSelectedDemandIds(),
                entity.getExternalReference(),
                entity.getSupplierRejectionCode(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getOpenedAt(),
                entity.getClosedAt(),
                entity.getClosedByUserAccountId());
    }
}
