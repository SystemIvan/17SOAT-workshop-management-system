package br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.model.PurchaseOrder;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.application.exception.PurchaseOrderIdempotencyRaceException;
import br.com.fiap.workshop_management_system.stockprocurement.purchaseorder.domain.repository.PurchaseOrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PurchaseOrderRepositoryImpl implements PurchaseOrderRepository {

    private final PurchaseOrderJpaRepository jpaRepository;
    private final PurchaseOrderPersistenceMapper mapper;

    public PurchaseOrderRepositoryImpl(
            PurchaseOrderJpaRepository jpaRepository,
            PurchaseOrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<PurchaseOrder> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseOrder> findByIdForUpdate(UUID id) {
        return jpaRepository.findByIdForUpdate(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PurchaseOrder> findByIdempotencyKey(UUID idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(mapper::toDomain);
    }

    @Override
    public void save(PurchaseOrder purchaseOrder) {
        jpaRepository.save(mapper.toEntity(purchaseOrder));
    }

    @Override
    public void saveAndFlush(PurchaseOrder purchaseOrder) {
        try {
            jpaRepository.saveAndFlush(mapper.toEntity(purchaseOrder));
        } catch (DataIntegrityViolationException exception) {
            throw new PurchaseOrderIdempotencyRaceException(exception);
        }
    }
}
