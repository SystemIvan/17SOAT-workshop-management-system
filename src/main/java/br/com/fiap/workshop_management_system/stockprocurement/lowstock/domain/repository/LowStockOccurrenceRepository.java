package br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LowStockOccurrenceRepository {

    Optional<LowStockOccurrence> findById(UUID id);

    Optional<LowStockOccurrence> findOpenByStockItemIdForUpdate(UUID stockItemId);

    List<LowStockOccurrence> findOpenByStockItemIds(Collection<UUID> stockItemIds);

    Optional<LowStockOccurrence> findByPurchaseDemandIdForUpdate(UUID purchaseDemandId);

    void save(LowStockOccurrence occurrence);
}
