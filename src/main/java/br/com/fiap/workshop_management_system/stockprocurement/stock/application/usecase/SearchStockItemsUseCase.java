package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemSearchCriteria;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository.LowStockOccurrenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SearchStockItemsUseCase {
    private final StockItemRepository repository;
    private final LowStockOccurrenceRepository occurrenceRepository;

    @Autowired
    public SearchStockItemsUseCase(StockItemRepository repository, LowStockOccurrenceRepository occurrenceRepository) {
        this.repository = repository;
        this.occurrenceRepository = occurrenceRepository;
    }

    public SearchStockItemsUseCase(StockItemRepository repository) {
        this.repository = repository;
        this.occurrenceRepository = null;
    }
    @Transactional(readOnly = true)
    public List<StockItemResponse> execute(StockItemSearchCriteria criteria) {
        var items = repository.search(criteria);
        var occurrences = occurrenceRepository == null ? java.util.Map.<java.util.UUID,
                br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrence>of()
                : occurrenceRepository.findOpenByStockItemIds(items.stream().map(item -> item.id()).toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(occurrence -> occurrence.stockItemId(), occurrence -> occurrence));
        return items.stream().map(item -> StockItemMapper.toResponse(item, occurrences.get(item.id()))).toList();
    }
}
