package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseDemandRepository {

    Optional<PurchaseDemand> findById(UUID id);

    Optional<PurchaseDemand> findEquivalentForUpdate(
            PurchaseDemandOrigin origin,
            UUID originReferenceId,
            UUID stockItemId);

    List<PurchaseDemand> findAllByIdForUpdate(List<UUID> ids);

    List<PurchaseDemand> findOpenByOriginReferenceAndStockItems(
            PurchaseDemandOrigin origin,
            UUID originReferenceId,
            Collection<UUID> stockItemIds);

    List<PurchaseDemand> searchOpen(PurchaseDemandSearchCriteria criteria);

    void save(PurchaseDemand purchaseDemand);

    void saveAll(Collection<PurchaseDemand> purchaseDemands);
}
