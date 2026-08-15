package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetStockItemUseCase {
    private final StockItemRepository repository;
    public GetStockItemUseCase(StockItemRepository repository) { this.repository = repository; }
    @Transactional(readOnly = true)
    public StockItemResponse execute(UUID id) {
        return StockItemMapper.toResponse(StockItemFinder.getOrThrow(repository, id));
    }
}
