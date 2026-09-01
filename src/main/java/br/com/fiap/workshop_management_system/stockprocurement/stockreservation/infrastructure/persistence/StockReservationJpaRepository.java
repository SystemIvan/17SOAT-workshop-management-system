package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

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

public interface StockReservationJpaRepository extends JpaRepository<StockReservationJpaEntity, UUID> {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<StockReservationJpaEntity> findById(UUID id);

    @EntityGraph(attributePaths = "lines")
    Optional<StockReservationJpaEntity> findByServiceExecutionId(UUID serviceExecutionId);

    @EntityGraph(attributePaths = "lines")
    List<StockReservationJpaEntity> findByServiceExecutionIdIn(Collection<UUID> serviceExecutionIds);

    @EntityGraph(attributePaths = "lines")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from StockReservationJpaEntity reservation where reservation.id = :id")
    Optional<StockReservationJpaEntity> findByIdForUpdate(@Param("id") UUID id);
}
