package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockReservationRepository {

    Optional<StockReservation> findById(UUID id);

    Optional<StockReservation> findByIdForUpdate(UUID id);

    Optional<StockReservation> findByServiceExecutionId(UUID serviceExecutionId);

    List<StockReservation> findByServiceExecutionIdIn(Collection<UUID> serviceExecutionIds);

    void save(StockReservation stockReservation);
}
