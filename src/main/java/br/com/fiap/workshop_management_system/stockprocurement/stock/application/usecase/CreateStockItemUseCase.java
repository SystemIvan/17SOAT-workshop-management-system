package br.com.fiap.workshop_management_system.stockprocurement.stock.application.usecase;

import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.CreateStockItemRequest;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemMapper;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.dto.StockItemResponse;
import br.com.fiap.workshop_management_system.stockprocurement.stock.application.exception
        .StockItemSkuAlreadyExistsException;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.application.usecase.EvaluateLowStockUseCase;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.LowStockPolicy;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Quantity;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateStockItemUseCase {
    private final StockItemRepository repository;
    private final EvaluateLowStockUseCase evaluator;

    @Autowired
    public CreateStockItemUseCase(StockItemRepository repository, EvaluateLowStockUseCase evaluator) {
        this.repository = repository;
        this.evaluator = evaluator;
    }

    CreateStockItemUseCase(StockItemRepository repository) {
        this.repository = repository;
        this.evaluator = null;
    }

    @Transactional
    public StockItemResponse execute(CreateStockItemRequest request) {
        Sku sku = new Sku(request.sku());
        if (repository.existsBySku(sku)) {
            throw new StockItemSkuAlreadyExistsException();
        }
        LowStockPolicy policy = request.lowStockPolicy() == null ? null : new LowStockPolicy(
                new Quantity(request.lowStockPolicy().minimumQuantity()), new Quantity(request.lowStockPolicy().targetQuantity()));
        StockItem item = StockItem.create(sku, request.name(), request.type(), StockItemMapper.toPrice(request.price()),
                new Quantity(request.availableQuantity()), policy);
        repository.save(item);
        var occurrence = evaluator != null && policy != null
                ? evaluator.evaluateLockedStockItem(item).orElse(null)
                : null;
        return StockItemMapper.toResponse(item, occurrence);
    }
}
