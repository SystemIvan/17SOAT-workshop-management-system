package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.UpdateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateStockItemUseCase {
    private final StockItemRepository repository;
    public UpdateStockItemUseCase(StockItemRepository repository) { this.repository = repository; }
    @Transactional
    public StockItemResponse execute(UUID id, UpdateStockItemRequest request) {
        var item = StockItemFinder.getOrThrowForUpdate(repository, id);
        item.updateDetails(request.name(), request.price() == null ? null : StockItemMapper.toPrice(request.price()));
        repository.save(item);
        return StockItemMapper.toResponse(item);
    }
}
