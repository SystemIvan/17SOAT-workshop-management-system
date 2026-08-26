package br.com.fiap.workshop_management_system.stockprocurement.lowstock.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrenceStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LowStockOccurrenceJpaRepository extends JpaRepository<LowStockOccurrenceJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select occurrence from LowStockOccurrenceJpaEntity occurrence "
            + "where occurrence.stockItemId = :stockItemId and occurrence.status = 'OPEN'")
    Optional<LowStockOccurrenceJpaEntity> findOpenByStockItemIdForUpdate(@Param("stockItemId") UUID stockItemId);

    @Query("select occurrence from LowStockOccurrenceJpaEntity occurrence "
            + "where occurrence.stockItemId in :stockItemIds and occurrence.status = 'OPEN'")
    List<LowStockOccurrenceJpaEntity> findOpenByStockItemIds(@Param("stockItemIds") Collection<UUID> stockItemIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LowStockOccurrenceJpaEntity> findByPurchaseDemandId(UUID purchaseDemandId);
}
