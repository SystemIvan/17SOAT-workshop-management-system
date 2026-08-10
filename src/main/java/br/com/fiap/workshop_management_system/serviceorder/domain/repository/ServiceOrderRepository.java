package br.com.fiap.workshop_management_system.serviceorder.domain.repository;

import br.com.fiap.workshop_management_system.serviceorder.domain.model.ServiceOrder;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for the ServiceOrder aggregate. Each Aggregate Root has its own repository;
 * transactional consistency is restricted to the aggregate's own boundary.
 */
public interface ServiceOrderRepository {

    Optional<ServiceOrder> findById(UUID id);

    void save(ServiceOrder serviceOrder);
}