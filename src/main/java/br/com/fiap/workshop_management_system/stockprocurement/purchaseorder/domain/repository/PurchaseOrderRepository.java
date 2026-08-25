package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository {

    Optional<PurchaseOrder> findById(UUID id);

    Optional<PurchaseOrder> findByIdForUpdate(UUID id);

    Optional<PurchaseOrder> findByIdempotencyKey(UUID idempotencyKey);

    void save(PurchaseOrder purchaseOrder);

    void saveAndFlush(PurchaseOrder purchaseOrder);
}
