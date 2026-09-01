package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockAvailability;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class EstimatePersistenceMapper {

    public EstimateJpaEntity toEntity(Estimate estimate) {
        EstimateJpaEntity entity = new EstimateJpaEntity(
                estimate.id(),
                estimate.serviceOrderId(),
                estimate.diagnosisId(),
                estimate.customerId(),
                estimate.createdAt(),
                estimate.expiresAt(),
                estimate.status()
        );

        for (int lineIndex = 0; lineIndex < estimate.lines().size(); lineIndex++) {
            EstimateLine line = estimate.lines().get(lineIndex);

            EstimateLineJpaEntity lineEntity = new EstimateLineJpaEntity(
                    UUID.randomUUID(),
                    lineIndex,
                    line.serviceExecutionId(),
                    line.serviceName(),
                    line.servicePrice().value(),
                    line.servicePrice().currency()
            );

            for (int itemIndex = 0; itemIndex < line.stockItems().size(); itemIndex++) {
                EstimateStockItem item = line.stockItems().get(itemIndex);

                EstimateStockItemJpaEntity itemEntity =
                        new EstimateStockItemJpaEntity(
                                UUID.randomUUID(),
                                itemIndex,
                                item.stockItemId(),
                                item.type(),
                                item.quantity(),
                                item.nameSnapshot(),
                                item.priceSnapshot().value(),
                                item.priceSnapshot().currency()
                        );

                lineEntity.addStockItem(itemEntity);
            }

            for (EstimateStockAvailability availability : line.stockAvailability()) {
                lineEntity.addStockAvailability(new EstimateStockAvailabilityJpaEntity(
                        availability.stockItemId(), availability.requestedQuantity(),
                        availability.observedAvailableQuantity(), availability.shortageQuantity(), availability.status(),
                        availability.observedAt()));
            }

            entity.addLine(lineEntity);
        }

        return entity;
    }

    public Estimate toDomain(EstimateJpaEntity entity) {
        List<EstimateLine> lines = entity.getLines().stream()
                .sorted(Comparator.comparingInt(EstimateLineJpaEntity::getLineOrder))
                .map(this::toDomainLine)
                .toList();

        return Estimate.reconstitute(
                entity.getId(),
                entity.getServiceOrderId(),
                entity.getDiagnosisId(),
                entity.getCustomerId(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                lines,
                entity.getStatus()
        );
    }

    private EstimateLine toDomainLine(EstimateLineJpaEntity entity) {
        List<EstimateStockItem> stockItems =
                entity.getStockItems().stream()
                        .sorted(Comparator.comparingInt(
                                EstimateStockItemJpaEntity::getItemOrder
                        ))
                        .map(this::toDomainStockItem)
                        .toList();
        List<EstimateStockAvailability> stockAvailability = entity.getStockAvailability().stream()
                .map(availability -> new EstimateStockAvailability(
                        availability.getStockItemId(), availability.getRequestedQuantity(),
                        availability.getObservedAvailableQuantity(), availability.getShortageQuantity(),
                        availability.getStatus(), availability.getObservedAt()))
                .toList();

        return new EstimateLine(
                entity.getServiceExecutionId(),
                entity.getServiceName(),
                new Money(
                        entity.getServicePriceValue(),
                        entity.getServicePriceCurrency()
                ),
                stockItems,
                stockAvailability
        );
    }

    private EstimateStockItem toDomainStockItem(
            EstimateStockItemJpaEntity entity) {

        return new EstimateStockItem(
                entity.getStockItemId(),
                entity.getType(),
                entity.getQuantity(),
                entity.getNameSnapshot(),
                new Money(
                        entity.getPriceSnapshotValue(),
                        entity.getPriceSnapshotCurrency()
                )
        );
    }
}
