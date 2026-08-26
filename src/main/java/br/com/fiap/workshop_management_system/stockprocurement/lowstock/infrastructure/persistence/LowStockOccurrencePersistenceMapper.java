package br.com.fiap.workshop_management_system.stockprocurement.lowstock.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrence;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrenceStatus;
import org.springframework.stereotype.Component;

@Component
public class LowStockOccurrencePersistenceMapper {

    public LowStockOccurrenceJpaEntity toEntity(LowStockOccurrence occurrence) {
        Integer openSlot = occurrence.status() == LowStockOccurrenceStatus.OPEN ? 1 : null;
        return new LowStockOccurrenceJpaEntity(occurrence.id(), occurrence.stockItemId(), occurrence.purchaseDemandId(),
                occurrence.status(), openSlot, occurrence.observedAvailableQuantity(), occurrence.suggestedQuantity(),
                occurrence.detectedAt(), occurrence.updatedAt(), occurrence.closedAt(), occurrence.closureReason());
    }

    public LowStockOccurrence toDomain(LowStockOccurrenceJpaEntity entity) {
        return LowStockOccurrence.reconstitute(entity.getId(), entity.getStockItemId(), entity.getPurchaseDemandId(),
                entity.getStatus(), entity.getObservedAvailableQuantity(), entity.getSuggestedQuantity(),
                entity.getDetectedAt(), entity.getUpdatedAt(), entity.getClosedAt(), entity.getClosureReason());
    }
}
