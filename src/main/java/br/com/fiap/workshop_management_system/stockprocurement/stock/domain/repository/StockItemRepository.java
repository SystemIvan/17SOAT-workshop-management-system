package br.com.fiap.workshop_management_system.stockprocurement.stock.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.Sku;
import br.com.fiap.workshop_management_system.stockprocurement.stock.domain.model.StockItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository {
    Optional<StockItem> findById(UUID id);
    Optional<StockItem> findByIdForUpdate(UUID id);
    List<StockItem> findAllByIdForUpdate(List<UUID> ids);
    boolean existsBySku(Sku sku);
    List<StockItem> search(StockItemSearchCriteria criteria);
    void save(StockItem stockItem);
}
