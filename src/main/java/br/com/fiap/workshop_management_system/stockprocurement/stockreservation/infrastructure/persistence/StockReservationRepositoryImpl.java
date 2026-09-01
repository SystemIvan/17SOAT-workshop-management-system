package br.com.fiap.workshop_management_system.stockprocurement.stockreservation.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.model.StockReservation;
import br.com.fiap.workshop_management_system.stockprocurement.stockreservation.domain.repository.StockReservationRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StockReservationRepositoryImpl implements StockReservationRepository {

    private final StockReservationJpaRepository jpaRepository;
    private final StockReservationPersistenceMapper mapper;

    public StockReservationRepositoryImpl(
            StockReservationJpaRepository jpaRepository,
            StockReservationPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<StockReservation> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<StockReservation> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Optional<StockReservation> findByServiceExecutionId(UUID serviceExecutionId) {
        return jpaRepository.findByServiceExecutionId(serviceExecutionId).map(mapper::toDomain);
    }

    @Override
    public List<StockReservation> findByServiceExecutionIdIn(Collection<UUID> serviceExecutionIds) {
        return jpaRepository.findByServiceExecutionIdIn(serviceExecutionIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(StockReservation stockReservation) {
        jpaRepository.save(mapper.toEntity(stockReservation));
    }
}
