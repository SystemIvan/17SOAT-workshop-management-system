package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservationLine;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StockReservationPersistenceMapper {

    public StockReservationJpaEntity toEntity(StockReservation reservation) {
        List<StockReservationLineEmbeddable> lines = reservation.lines().stream()
                .map(line -> new StockReservationLineEmbeddable(line.stockItemId(), line.quantity()))
                .toList();
        return new StockReservationJpaEntity(
                reservation.id(),
                reservation.serviceExecutionId(),
                reservation.status(),
                reservation.createdAt(),
                reservation.consumedAt(),
                lines);
    }

    public StockReservation toDomain(StockReservationJpaEntity entity) {
        List<StockReservationLine> lines = entity.getLines().stream()
                .map(line -> new StockReservationLine(line.getStockItemId(), line.getQuantity()))
                .toList();
        return StockReservation.reconstitute(
                entity.getId(),
                entity.getServiceExecutionId(),
                lines,
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getConsumedAt());
    }
}
