package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderJpaRepository extends JpaRepository<PurchaseOrderJpaEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = {"lines", "selectedDemandIds"})
    Optional<PurchaseOrderJpaEntity> findById(UUID id);

    @EntityGraph(attributePaths = {"lines", "selectedDemandIds"})
    Optional<PurchaseOrderJpaEntity> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select purchaseOrder from PurchaseOrderJpaEntity purchaseOrder where purchaseOrder.id = :id")
    Optional<PurchaseOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
