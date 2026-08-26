package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.usecase.EvaluateLowStockUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.LowStockPolicyCommand;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.LowStockPolicy;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConfigureLowStockPolicyUseCase {

    private final StockItemRepository repository;
    private final EvaluateLowStockUseCase evaluator;

    public ConfigureLowStockPolicyUseCase(StockItemRepository repository, EvaluateLowStockUseCase evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    @Transactional
    public StockItemResponse execute(UUID stockItemId, LowStockPolicyCommand command) {
        var item = StockItemFinder.getOrThrowForUpdate(repository, stockItemId);
        item.configureLowStockPolicy(new LowStockPolicy(
                new Quantity(command.minimumQuantity()), new Quantity(command.targetQuantity())));
        repository.save(item);
        return StockItemMapper.toResponse(item, evaluator.evaluateLockedStockItem(item).orElse(null));
    }
}
