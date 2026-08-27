package br.com.fiap.workshop_management_system.stockprocurement.lowstock.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.model.LowStockOccurrence;
import br.com.fiap.workshop_management_system.stockprocurement.lowstock.domain.repository.LowStockOccurrenceRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LowStockOccurrenceRepositoryImpl implements LowStockOccurrenceRepository {

    private final LowStockOccurrenceJpaRepository jpaRepository;
    private final LowStockOccurrencePersistenceMapper mapper;

    public LowStockOccurrenceRepositoryImpl(LowStockOccurrenceJpaRepository jpaRepository,
                                            LowStockOccurrencePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<LowStockOccurrence> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<LowStockOccurrence> findOpenByStockItemIdForUpdate(UUID stockItemId) {
        return jpaRepository.findOpenByStockItemIdForUpdate(stockItemId).map(mapper::toDomain);
    }

    @Override
    public List<LowStockOccurrence> findOpenByStockItemIds(Collection<UUID> stockItemIds) {
        if (stockItemIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findOpenByStockItemIds(stockItemIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<LowStockOccurrence> findByPurchaseDemandIdForUpdate(UUID purchaseDemandId) {
        return jpaRepository.findByPurchaseDemandId(purchaseDemandId).map(mapper::toDomain);
    }

    @Override
    public void save(LowStockOccurrence occurrence) {
        jpaRepository.saveAndFlush(mapper.toEntity(occurrence));
    }
}
