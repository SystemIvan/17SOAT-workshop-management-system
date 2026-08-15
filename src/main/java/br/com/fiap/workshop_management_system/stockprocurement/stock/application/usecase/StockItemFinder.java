package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception.StockItemNotFoundException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;

import java.util.UUID;

final class StockItemFinder {
    private StockItemFinder() {
    }

    static StockItem getOrThrow(StockItemRepository repository, UUID id) {
        return repository.findById(id).orElseThrow(StockItemNotFoundException::new);
    }
}
