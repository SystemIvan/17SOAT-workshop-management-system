package br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.listener;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.usecase.EvaluateLowStockUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockClosureReason;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository.LowStockOccurrenceRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.application.event.StockItemsRestockedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class RestockedLowStockReevaluationListener {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockItemRepository stockItemRepository;
    private final LowStockOccurrenceRepository occurrenceRepository;
    private final EvaluateLowStockUseCase evaluator;

    public RestockedLowStockReevaluationListener(PurchaseOrderRepository purchaseOrderRepository,
                                                 StockItemRepository stockItemRepository,
                                                 LowStockOccurrenceRepository occurrenceRepository,
                                                 EvaluateLowStockUseCase evaluator) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.stockItemRepository = stockItemRepository;
        this.occurrenceRepository = occurrenceRepository;
        this.evaluator = evaluator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(StockItemsRestockedEvent event) {
        var purchaseOrder = purchaseOrderRepository.findById(event.purchaseOrderId()).orElse(null);
        if (purchaseOrder == null) {
            return;
        }
        Set<UUID> selectedDemandIds = Set.copyOf(purchaseOrder.selectedDemandIds());
        Map<UUID, br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem> items =
                new HashMap<>();
        stockItemRepository.findAllByIdForUpdate(event.stockItemIds().stream().sorted().toList())
                .forEach(item -> items.put(item.id(), item));

        for (UUID stockItemId : event.stockItemIds().stream().sorted().toList()) {
            var stockItem = items.get(stockItemId);
            if (stockItem == null) {
                continue;
            }
            occurrenceRepository.findOpenByStockItemIdForUpdate(stockItemId)
                    .filter(occurrence -> selectedDemandIds.contains(occurrence.purchaseDemandId()))
                    .ifPresent(occurrence -> evaluator.closeOpenOccurrence(
                            stockItemId, LowStockClosureReason.REPLENISHMENT_CYCLE_COMPLETED));
            evaluator.evaluateLockedStockItem(stockItem);
        }
    }
}
