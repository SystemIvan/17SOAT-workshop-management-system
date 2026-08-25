package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemand;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandOrigin;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseDemandStatus;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandRepository;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseDemandSearchCriteria;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PurchaseDemandRepositoryImpl implements PurchaseDemandRepository {

    private final PurchaseDemandJpaRepository jpaRepository;
    private final PurchaseDemandPersistenceMapper mapper;

    public PurchaseDemandRepositoryImpl(
            PurchaseDemandJpaRepository jpaRepository,
            PurchaseDemandPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PurchaseDemand> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseDemand> findEquivalentForUpdate(
            PurchaseDemandOrigin origin,
            UUID originReferenceId,
            UUID stockItemId) {
        return jpaRepository.findEquivalentForUpdate(origin, originReferenceId, stockItemId).map(mapper::toDomain);
    }

    @Override
    public List<PurchaseDemand> findAllByIdForUpdate(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByIdForUpdate(ids).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<PurchaseDemand> findOpenByOriginReferenceAndStockItems(
            PurchaseDemandOrigin origin,
            UUID originReferenceId,
            Collection<UUID> stockItemIds) {
        if (stockItemIds.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findOpenByOriginReferenceAndStockItems(
                        PurchaseDemandStatus.OPEN, origin, originReferenceId, stockItemIds).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<PurchaseDemand> searchOpen(PurchaseDemandSearchCriteria criteria) {
        return jpaRepository.searchOpen(
                        PurchaseDemandStatus.OPEN, criteria.origin(), criteria.stockItemId()).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public void save(PurchaseDemand purchaseDemand) {
        jpaRepository.save(mapper.toEntity(purchaseDemand));
    }

    @Override
    public void saveAll(Collection<PurchaseDemand> purchaseDemands) {
        jpaRepository.saveAll(purchaseDemands.stream().map(mapper::toEntity).toList());
    }
}
