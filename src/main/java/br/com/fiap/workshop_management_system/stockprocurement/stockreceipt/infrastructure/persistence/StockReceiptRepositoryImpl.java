package br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.model.StockReceipt;
import br.com.fiap.workshop_management_system.stockprocurement.stockreceipt.domain.repository.StockReceiptRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class StockReceiptRepositoryImpl implements StockReceiptRepository {

    private final StockReceiptJpaRepository jpaRepository;
    private final StockReceiptPersistenceMapper mapper;

    public StockReceiptRepositoryImpl(StockReceiptJpaRepository jpaRepository, StockReceiptPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<StockReceipt> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<StockReceipt> findByPurchaseOrderId(UUID purchaseOrderId) {
        return jpaRepository.findByPurchaseOrderId(purchaseOrderId).map(mapper::toDomain);
    }

    @Override
    public Optional<StockReceipt> findByPurchaseOrderIdForUpdate(UUID purchaseOrderId) {
        return jpaRepository.findByPurchaseOrderIdForUpdate(purchaseOrderId).map(mapper::toDomain);
    }

    @Override
    public List<StockReceipt> findByPurchaseOrderIds(Collection<UUID> purchaseOrderIds) {
        return jpaRepository.findByPurchaseOrderIdIn(purchaseOrderIds).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void save(StockReceipt receipt) {
        jpaRepository.save(mapper.toEntity(Objects.requireNonNull(receipt, "receipt must not be null")));
    }
}
