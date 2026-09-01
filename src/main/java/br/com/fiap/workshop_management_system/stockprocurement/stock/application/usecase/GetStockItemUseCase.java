package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository.LowStockOccurrenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetStockItemUseCase {
    private final StockItemRepository repository;
    private final LowStockOccurrenceRepository occurrenceRepository;

    @Autowired
    public GetStockItemUseCase(StockItemRepository repository, LowStockOccurrenceRepository occurrenceRepository) {
        this.repository = repository;
        this.occurrenceRepository = occurrenceRepository;
    }

    GetStockItemUseCase(StockItemRepository repository) {
        this.repository = repository;
        this.occurrenceRepository = null;
    }
    @Transactional(readOnly = true)
    public StockItemResponse execute(UUID id) {
        var item = StockItemFinder.getOrThrow(repository, id);
        var occurrence = occurrenceRepository == null ? null
                : occurrenceRepository.findOpenByStockItemIds(java.util.List.of(id)).stream().findFirst().orElse(null);
        return StockItemMapper.toResponse(item, occurrence);
    }
}
