package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrderStatus;

public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrderJpaEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"lines", "selectedDemandIds"})
    Optional<PurchaseOrderJpaEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"lines", "selectedDemandIds"})
    Optional<PurchaseOrderJpaEntity> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrderJpaEntity purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"lines", "selectedDemandIds"})
    @Query("select purchaseOrder from PurchaseOrderJpaEntity purchaseOrder "
            + "where purchaseOrder.status in :statuses order by purchaseOrder.updatedAt desc, purchaseOrder.id asc")
    List<PurchaseOrderJpaEntity> findByStatusInOrderByUpdatedAtDescIdAsc(
            @Param("statuses") Set<PurchaseOrderStatus> statuses);
}
