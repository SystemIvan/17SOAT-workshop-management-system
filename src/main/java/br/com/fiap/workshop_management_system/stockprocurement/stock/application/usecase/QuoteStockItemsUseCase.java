package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.api.StockItemQuote;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.api.StockItemQuoteApi;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class QuoteStockItemsUseCase implements StockItemQuoteApi {

    private final StockItemRepository repository;

    public QuoteStockItemsUseCase(StockItemRepository repository) {
        this.repository = repository;
    }

    // Sorted IDs keep the lock order identical to RepairStockAssessment, avoiding deadlocks between the two.
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<StockItemQuote> quote(Collection<UUID> stockItemIds) {
        Objects.requireNonNull(stockItemIds, "Os IDs dos itens de estoque são obrigatórios");
        List<UUID> ids = stockItemIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findAllByIdForUpdate(ids).stream()
                .map(QuoteStockItemsUseCase::toQuote)
                .toList();
    }

    private static StockItemQuote toQuote(StockItem item) {
        return new StockItemQuote(item.id(), item.active(), item.name(), item.type().name(), item.price().value(),
                item.price().currency().name());
    }
}
