package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ServiceOrderJpaRepository extends JpaRepository<ServiceOrderJpaEntity, UUID>,
        JpaSpecificationExecutor<ServiceOrderJpaEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select serviceOrder from ServiceOrderJpaEntity serviceOrder where serviceOrder.id = :id")
    Optional<ServiceOrderJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select distinct serviceOrder from ServiceOrderJpaEntity serviceOrder "
            + "join fetch serviceOrder.executions execution "
            + "join fetch execution.stockRequirements requirement "
            + "where execution.status = br.com.fiap.workshop_management_system.servicelifecycle.serviceorder"
            + ".domain.model.ServiceExecutionStatus.AWAITING_ITEMS and requirement.stockItemId in :stockItemIds")
    List<ServiceOrderJpaEntity> findAwaitingItemsByStockItemIds(@Param("stockItemIds") Collection<UUID> stockItemIds);
}
