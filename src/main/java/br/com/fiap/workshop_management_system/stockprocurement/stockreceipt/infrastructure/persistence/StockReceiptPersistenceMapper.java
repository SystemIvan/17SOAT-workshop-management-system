package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceiptLine;
import org.springframework.stereotype.Component;

@Component
public class StockReceiptPersistenceMapper {

    public StockReceiptJpaEntity toEntity(StockReceipt receipt) {
        return new StockReceiptJpaEntity(
                receipt.id(),
                receipt.purchaseOrderId(),
                receipt.receivedByUserAccountId(),
                receipt.receivedAt(),
                receipt.lines().stream()
                        .map(line -> new StockReceiptLineEmbeddable(
                                line.movementId(),
                                line.stockItemId(),
                                line.quantity(),
                                line.availableBefore(),
                                line.availableAfter()))
                        .toList());
    }

    public StockReceipt toDomain(StockReceiptJpaEntity entity) {
        return StockReceipt.reconstitute(
                entity.getId(),
                entity.getPurchaseOrderId(),
                entity.getReceivedByUserAccountId(),
                entity.getReceivedAt(),
                entity.getLines().stream()
                        .map(line -> new StockReceiptLine(
                                line.getMovementId(),
                                line.getStockItemId(),
                                line.getQuantity(),
                                line.getAvailableBefore(),
                                line.getAvailableAfter()))
                        .toList());
    }
}
