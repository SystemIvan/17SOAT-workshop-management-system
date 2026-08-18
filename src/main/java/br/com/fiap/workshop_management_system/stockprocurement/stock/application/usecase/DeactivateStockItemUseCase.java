package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DeactivateStockItemUseCase {
    private final StockItemRepository repository;
    public DeactivateStockItemUseCase(StockItemRepository repository) { this.repository = repository; }
    @Transactional
    public void execute(UUID id) {
        var item = StockItemFinder.getOrThrow(repository, id);
        item.deactivate();
        repository.save(item);
    }
}
