package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReceiptRepository {

    Optional<StockReceipt> findById(UUID id);

    Optional<StockReceipt> findByPurchaseOrderId(UUID purchaseOrderId);

    Optional<StockReceipt> findByPurchaseOrderIdForUpdate(UUID purchaseOrderId);

    List<StockReceipt> findByPurchaseOrderIds(Collection<UUID> purchaseOrderIds);

    void save(StockReceipt receipt);
}
