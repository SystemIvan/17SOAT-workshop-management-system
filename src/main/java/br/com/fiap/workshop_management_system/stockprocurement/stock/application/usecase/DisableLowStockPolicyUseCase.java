package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.usecase.EvaluateLowStockUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockClosureReason;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DisableLowStockPolicyUseCase {

    private final StockItemRepository repository;
    private final EvaluateLowStockUseCase evaluator;

    public DisableLowStockPolicyUseCase(StockItemRepository repository, EvaluateLowStockUseCase evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    @Transactional
    public void execute(UUID stockItemId) {
        var item = StockItemFinder.getOrThrowForUpdate(repository, stockItemId);
        evaluator.closeOpenOccurrence(item.id(), LowStockClosureReason.POLICY_DISABLED);
        item.disableLowStockPolicy();
        repository.save(item);
    }
}
