package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.CreateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception
        .StockItemSkuAlreadyExistsException;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateStockItemUseCase {
    private final StockItemRepository repository;

    public CreateStockItemUseCase(StockItemRepository repository) { this.repository = repository; }

    @Transactional
    public StockItemResponse execute(CreateStockItemRequest request) {
        Sku sku = new Sku(request.sku());
        if (repository.existsBySku(sku)) {
            throw new StockItemSkuAlreadyExistsException();
        }
        StockItem item = StockItem.create(sku, request.name(), request.type(), StockItemMapper.toPrice(request.price()),
                new Quantity(request.availableQuantity()));
        repository.save(item);
        return StockItemMapper.toResponse(item);
    }
}
