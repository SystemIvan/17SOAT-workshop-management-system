package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReceiptJpaRepository extends JpaRepository<StockReceiptJpaEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<StockReceiptJpaEntity> findById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<StockReceiptJpaEntity> findByPurchaseOrderId(UUID purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "lines")
    @Query("select receipt from StockReceiptJpaEntity receipt where receipt.purchaseOrderId = :purchaseOrderId")
    Optional<StockReceiptJpaEntity> findByPurchaseOrderIdForUpdate(@Param("purchaseOrderId") UUID purchaseOrderId);

    @EntityGraph(attributePaths = "lines")
    List<StockReceiptJpaEntity> findByPurchaseOrderIdIn(Collection<UUID> purchaseOrderIds);
}
