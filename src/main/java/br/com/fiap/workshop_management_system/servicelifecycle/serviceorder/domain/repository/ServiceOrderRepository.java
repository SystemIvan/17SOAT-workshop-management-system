package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port for the ServiceOrder aggregate. Each Aggregate Root has its own repository;
 * transactional consistency is restricted to the aggregate's own boundary.
 */
public interface ServiceOrderRepository {

    Optional<ServiceOrder> findById(UUID id);

    default Optional<ServiceOrder> findByIdForUpdate(UUID id) {
        return findById(id);
    }

    /**
     * Default implementation throws so the many existing test fakes of this interface (none of which
     * exercise search) don't need to implement it; only the production JPA adapter and fakes that
     * actually need search override it.
     */
    default List<ServiceOrder> search(ServiceOrderSearchCriteria criteria) {
        throw new UnsupportedOperationException("search not supported by this ServiceOrderRepository");
    }

    default List<StockReservationRetryCandidate> findAwaitingItemsByStockItemIds(
            java.util.Collection<UUID> stockItemIds) {
        throw new UnsupportedOperationException("awaiting-items search not supported by this ServiceOrderRepository");
    }

    void save(ServiceOrder serviceOrder);
}
