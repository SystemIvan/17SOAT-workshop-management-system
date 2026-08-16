package br.com.fiap.workshop_management_system.servicelifecycle.estimate.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.Estimate;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateLine;
import br.com.fiap.workshop_management_system.servicelifecycle.estimate.domain.model.EstimateStockItem;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.Money;
import org.springframework.stereotype.Component;

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
                estimate.expiresAt()
        );

        estimate.lines().forEach(line -> {
            EstimateLineJpaEntity lineEntity = new EstimateLineJpaEntity(
                    UUID.randomUUID(),
                    line.serviceExecutionId(),
                    line.serviceName(),
                    line.servicePrice().value(),
                    line.servicePrice().currency()
            );

            line.stockItems().forEach(item -> {
                EstimateStockItemJpaEntity itemEntity =
                        new EstimateStockItemJpaEntity(
                                UUID.randomUUID(),
                                item.stockItemId(),
                                item.type(),
                                item.quantity(),
                                item.nameSnapshot(),
                                item.priceSnapshot().value(),
                                item.priceSnapshot().currency()
                        );

                lineEntity.addStockItem(itemEntity);
            });

            entity.addLine(lineEntity);
        });

        return entity;
    }

    public Estimate toDomain(EstimateJpaEntity entity) {
        List<EstimateLine> lines = entity.getLines().stream()
                .map(this::toDomainLine)
                .toList();

        return Estimate.reconstitute(
                entity.getId(),
                entity.getServiceOrderId(),
                entity.getDiagnosisId(),
                entity.getCustomerId(),
                entity.getCreatedAt(),
                entity.getExpiresAt(),
                lines
        );
    }

    private EstimateLine toDomainLine(EstimateLineJpaEntity entity) {
        List<EstimateStockItem> stockItems =
                entity.getStockItems().stream()
                        .map(this::toDomainStockItem)
                        .toList();

        return new EstimateLine(
                entity.getServiceExecutionId(),
                entity.getServiceName(),
                new Money(
                        entity.getServicePriceValue(),
                        entity.getServicePriceCurrency()
                ),
                stockItems
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