package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import org.springframework.stereotype.Component;

@Component
public class PurchaseDemandPersistenceMapper {

    public PurchaseDemandJpaEntity toEntity(PurchaseDemand demand) {
        return new PurchaseDemandJpaEntity(
                demand.id(),
                demand.origin(),
                demand.originReferenceId(),
                demand.stockItemId(),
                demand.requestedQuantity(),
                demand.observedAvailableQuantity(),
                demand.suggestedQuantity(),
                demand.status(),
                demand.claimedByPurchaseOrderId(),
                demand.createdAt(),
                demand.updatedAt(),
                demand.resolvedAt());
    }

    public PurchaseDemand toDomain(PurchaseDemandJpaEntity entity) {
        return PurchaseDemand.reconstitute(
                entity.getId(),
                entity.getOrigin(),
                entity.getOriginReferenceId(),
                entity.getStockItemId(),
                entity.getRequestedQuantity(),
                entity.getObservedAvailableQuantity(),
                entity.getSuggestedQuantity(),
                entity.getStatus(),
                entity.getClaimedByPurchaseOrderId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getResolvedAt());
    }
}
