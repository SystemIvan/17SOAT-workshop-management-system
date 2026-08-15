package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchStockItemsUseCase {
    private final StockItemRepository repository;
    public SearchStockItemsUseCase(StockItemRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public List<StockItemResponse> execute(StockItemSearchCriteria criteria) {
        return repository.search(criteria).stream().map(StockItemMapper::toResponse).toList();
    }
}
