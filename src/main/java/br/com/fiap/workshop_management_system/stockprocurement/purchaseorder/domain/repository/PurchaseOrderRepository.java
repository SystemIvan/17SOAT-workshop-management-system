package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PurchaseOrderRepository {

    Optional<PurchaseOrder> findById(UUID id);

    Optional<PurchaseOrder> findByIdForUpdate(UUID id);

    Optional<PurchaseOrder> findByIdempotencyKey(UUID idempotencyKey);

    List<PurchaseOrder> searchConfirmedByStatus(Set<PurchaseOrderStatus> statuses);

    void save(PurchaseOrder purchaseOrder);

    void saveAndFlush(PurchaseOrder purchaseOrder);
}
