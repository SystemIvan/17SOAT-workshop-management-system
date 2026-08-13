package br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.infrastructure.persistence;

import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.model.ServiceOrder;
import br.com.fiap.workshop_management_system.servicelifecycle.serviceorder.domain.repository.ServiceOrderRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Infrastructure adapter for the {@link ServiceOrderRepository} port, backed by JPA.
 */
@Repository
public class ServiceOrderRepositoryImpl implements ServiceOrderRepository {

    private final ServiceOrderJpaRepository jpaRepository;
    private final ServiceOrderPersistenceMapper mapper;

    public ServiceOrderRepositoryImpl(ServiceOrderJpaRepository jpaRepository, ServiceOrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<ServiceOrder> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public void save(ServiceOrder serviceOrder) {
        jpaRepository.save(mapper.toEntity(serviceOrder));
    }
}
