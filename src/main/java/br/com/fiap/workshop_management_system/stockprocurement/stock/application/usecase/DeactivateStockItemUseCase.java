package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.usecase.EvaluateLowStockUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockClosureReason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeactivateStockItemUseCase {
    private final StockItemRepository repository;
    private final EvaluateLowStockUseCase evaluator;

    @Autowired
    public DeactivateStockItemUseCase(StockItemRepository repository, EvaluateLowStockUseCase evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    DeactivateStockItemUseCase(StockItemRepository repository) {
        this.repository = repository;
        this.evaluator = null;
    }
    @Transactional
    public void execute(UUID id) {
        var item = StockItemFinder.getOrThrowForUpdate(repository, id);
        if (evaluator != null) {
            evaluator.closeOpenOccurrence(item.id(), LowStockClosureReason.STOCK_ITEM_DEACTIVATED);
        }
        item.deactivate();
        repository.save(item);
    }
}
