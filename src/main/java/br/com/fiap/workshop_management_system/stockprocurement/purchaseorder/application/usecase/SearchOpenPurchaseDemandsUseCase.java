package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseDemandResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.dto.PurchaseDemandStockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandSearchCriteria;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SearchOpenPurchaseDemandsUseCase {

    private final PurchaseDemandRepository demandRepository;
    private final StockItemRepository stockItemRepository;

    public SearchOpenPurchaseDemandsUseCase(
            PurchaseDemandRepository demandRepository,
            StockItemRepository stockItemRepository) {
        this.demandRepository = demandRepository;
        this.stockItemRepository = stockItemRepository;
    }

    @Transactional(readOnly = true)
    public List<PurchaseDemandResponse> execute(PurchaseDemandOrigin origin, UUID stockItemId) {
        return demandRepository.searchOpen(new PurchaseDemandSearchCriteria(origin, stockItemId)).stream()
                .map(this::toResponse)
                .toList();
    }

    private PurchaseDemandResponse toResponse(PurchaseDemand demand) {
        StockItem stockItem = stockItemRepository.findById(demand.stockItemId())
                .orElseThrow(StockItemNotFoundException::new);
        return new PurchaseDemandResponse(
                demand.id(),
                demand.origin(),
                new PurchaseDemandStockItemResponse(
                        stockItem.id(),
                        stockItem.sku().value(),
                        stockItem.name(),
                        stockItem.type()),
                demand.requestedQuantity(),
                demand.observedAvailableQuantity(),
                demand.suggestedQuantity(),
                demand.origin() == PurchaseDemandOrigin.PENDING_REPAIR ? demand.originReferenceId() : null,
                demand.createdAt());
    }
}
