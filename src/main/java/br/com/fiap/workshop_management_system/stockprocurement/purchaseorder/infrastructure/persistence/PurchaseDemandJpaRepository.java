package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseDemandJpaRepository extends JpaRepository<PurchaseDemandJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select demand from PurchaseDemandJpaEntity demand
            where demand.origin = :origin
              and demand.originReferenceId = :originReferenceId
              and demand.stockItemId = :stockItemId
            """)
    Optional<PurchaseDemandJpaEntity> findEquivalentForUpdate(
            @Param("origin") PurchaseDemandOrigin origin,
            @Param("originReferenceId") UUID originReferenceId,
            @Param("stockItemId") UUID stockItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select demand from PurchaseDemandJpaEntity demand
            where demand.id in :ids
            order by demand.id
            """)
    List<PurchaseDemandJpaEntity> findAllByIdForUpdate(@Param("ids") Collection<UUID> ids);

    @Query("""
            select demand from PurchaseDemandJpaEntity demand
            where demand.status = :status
              and demand.origin = :origin
              and demand.originReferenceId = :originReferenceId
              and demand.stockItemId in :stockItemIds
            order by demand.id
            """)
    List<PurchaseDemandJpaEntity> findOpenByOriginReferenceAndStockItems(
            @Param("status") PurchaseDemandStatus status,
            @Param("origin") PurchaseDemandOrigin origin,
            @Param("originReferenceId") UUID originReferenceId,
            @Param("stockItemIds") Collection<UUID> stockItemIds);

    @Query("""
            select demand from PurchaseDemandJpaEntity demand
            where demand.status = :status
              and (:origin is null or demand.origin = :origin)
              and (:stockItemId is null or demand.stockItemId = :stockItemId)
            order by demand.createdAt, demand.id
            """)
    List<PurchaseDemandJpaEntity> searchOpen(
            @Param("status") PurchaseDemandStatus status,
            @Param("origin") PurchaseDemandOrigin origin,
            @Param("stockItemId") UUID stockItemId);
}
